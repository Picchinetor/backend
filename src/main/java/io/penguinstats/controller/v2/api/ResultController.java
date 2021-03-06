package io.penguinstats.controller.v2.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.penguinstats.constant.Constant.LastUpdateMapKeyName;
import io.penguinstats.constant.Constant.SystemPropertyKey;
import io.penguinstats.controller.v2.mapper.QueryMapper;
import io.penguinstats.controller.v2.request.AdvancedQueryRequest;
import io.penguinstats.controller.v2.response.AdvancedQueryResponse;
import io.penguinstats.controller.v2.response.BasicQueryResponse;
import io.penguinstats.controller.v2.response.MatrixQueryResponse;
import io.penguinstats.controller.v2.response.TrendQueryResponse;
import io.penguinstats.enums.QueryType;
import io.penguinstats.enums.Server;
import io.penguinstats.model.DropMatrixElement;
import io.penguinstats.model.query.BasicQuery;
import io.penguinstats.model.query.GlobalMatrixQuery;
import io.penguinstats.model.query.GlobalTrendQuery;
import io.penguinstats.model.query.QueryFactory;
import io.penguinstats.service.DropInfoService;
import io.penguinstats.service.SystemPropertyService;
import io.penguinstats.util.CookieUtil;
import io.penguinstats.util.DateUtil;
import io.penguinstats.util.LastUpdateTimeUtil;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController("resultController_v2")
@RequestMapping("/api/v2/result")
public class ResultController {

	private static Logger logger = LogManager.getLogger(ResultController.class);

	@Autowired
	private DropInfoService dropInfoService;
	@Autowired
	private SystemPropertyService systemPropertyService;
	@Autowired
	private CookieUtil cookieUtil;
	@Autowired
	private QueryMapper queryMapper;
	@Autowired
	private QueryFactory queryFactory;

	@ApiOperation("Get matrix")
	@GetMapping(path = "/matrix", produces = "application/json;charset=UTF-8")
	public ResponseEntity<MatrixQueryResponse> getMatrix(HttpServletRequest request,
			@RequestParam(name = "is_personal", required = false, defaultValue = "false") boolean isPersonal,
			@RequestParam(name = "show_closed_zones", required = false, defaultValue = "false") boolean showClosedZones,
			@RequestParam(name = "server", required = false, defaultValue = "CN") Server server) {
		logger.info("GET /matrix");
		try {
			String userID = isPersonal ? cookieUtil.readUserIDFromCookie(request) : null;

			GlobalMatrixQuery query = (GlobalMatrixQuery)queryFactory.getQuery(QueryType.GLOBAL_MATRIX);
			Integer timeout =
					systemPropertyService.getPropertyIntegerValue(SystemPropertyKey.GLOBAL_MATRIX_QUERY_TIMEOUT);
			query.setServer(server).setUserID(userID);
			if (timeout != null)
				query.setTimeout(timeout);
			List<DropMatrixElement> elements = query.execute();

			if (!showClosedZones)
				removeClosedStages(elements, server);

			MatrixQueryResponse result = new MatrixQueryResponse(elements);

			HttpHeaders headers = userID != null ? new HttpHeaders()
					: generateLastModifiedHeadersFromLastUpdateMap(LastUpdateMapKeyName.MATRIX_RESULT + "_" + server);

			return new ResponseEntity<MatrixQueryResponse>(result, headers, HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error in getMatrix", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiOperation("Get segmented drop data for all items in all stages")
	@GetMapping(path = "/trends", produces = "application/json;charset=UTF-8")
	public ResponseEntity<TrendQueryResponse> getAllSegmentedDropResults(
			@ApiParam(value = "The length of each section. Unit is \"day\".",
					required = false) @RequestParam(name = "interval_day", required = false) Integer interval,
			@ApiParam(
					value = "The total length of the time range used this query. The start time will be calculated using current time minus this value. Unit is \"day\".",
					required = false) @RequestParam(name = "range_day", required = false) Integer range,
			@ApiParam(value = "Indicate which server you want to query. Default is CN.",
					required = false) @RequestParam(name = "server", required = false,
							defaultValue = "CN") Server server) {
		try {
			if (interval == null)
				interval =
						systemPropertyService.getPropertyIntegerValue(SystemPropertyKey.DEFAULT_GLOBAL_TREND_INTERVAL);
			if (range == null)
				range = systemPropertyService.getPropertyIntegerValue(SystemPropertyKey.DEFAULT_GLOBAL_TREND_RANGE);

			GlobalTrendQuery query = (GlobalTrendQuery)queryFactory.getQuery(QueryType.GLOBAL_TREND);
			Integer timeout =
					systemPropertyService.getPropertyIntegerValue(SystemPropertyKey.GLOBAL_MATRIX_QUERY_TIMEOUT);
			query.setServer(server).setInterval(interval).setRange(range);
			if (timeout != null)
				query.setTimeout(timeout);
			List<DropMatrixElement> elements = query.execute();

			TrendQueryResponse result = new TrendQueryResponse(elements);

			HttpHeaders headers = generateLastModifiedHeadersFromLastUpdateMap(
					LastUpdateMapKeyName.TREND_RESULT + "_" + server + "_" + interval + "_" + range);

			return new ResponseEntity<TrendQueryResponse>(result, headers, HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error in getAllSegmentedDropResults: ", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiOperation("Execute advanced queries")
	@PostMapping(path = "/advanced", produces = "application/json;charset=UTF-8")
	public ResponseEntity<AdvancedQueryResponse> executeAdvancedQueries(
			@Valid @RequestBody AdvancedQueryRequest advancedQueryRequest, HttpServletRequest request) {
		Integer maxQueryNum =
				systemPropertyService.getPropertyIntegerValue(SystemPropertyKey.ADVANCED_QUERY_REQUEST_NUM_MAX);
		if (advancedQueryRequest.getQueries().size() > maxQueryNum) {
			AdvancedQueryResponse advancedQueryResponse =
					new AdvancedQueryResponse("Too many quiries. Max num is " + maxQueryNum);
			return new ResponseEntity<>(advancedQueryResponse, HttpStatus.BAD_REQUEST);
		}
		try {
			final String userIDFromCookie = cookieUtil.readUserIDFromCookie(request);
			List<BasicQueryResponse> results = new ArrayList<>();
			advancedQueryRequest.getQueries().forEach(singleQuery -> {
				try {
					Boolean isPersonal = Optional.ofNullable(singleQuery.getIsPersonal()).orElse(false);
					String userID = isPersonal ? userIDFromCookie : null;
					Integer timeout =
							systemPropertyService.getPropertyIntegerValue(SystemPropertyKey.ADVANCED_QUERY_TIMEOUT);
					BasicQuery query = queryMapper.queryRequestToQueryModel(singleQuery, userID, timeout);
					List<DropMatrixElement> elements = query.execute();
					BasicQueryResponse queryResponse = queryMapper.elementsToBasicQueryResponse(singleQuery, elements);
					results.add(queryResponse);
				} catch (TimeoutException toEx) {
					logger.error("TimeoutException in executeAdvancedQueries: ", toEx);
				} catch (ExecutionException exeEx) {
					logger.error("ExecutionException in executeAdvancedQueries: ", exeEx);
				} catch (Exception ex) {
					logger.error("Error in executeAdvancedQueries: ", ex);
				}
			});
			AdvancedQueryResponse advancedQueryResponse = new AdvancedQueryResponse(results);
			return new ResponseEntity<AdvancedQueryResponse>(advancedQueryResponse, HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error in executeAdvancedQueries: ", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private void removeClosedStages(List<DropMatrixElement> elements, Server server) {
		Set<String> openingStages = dropInfoService.getOpeningStages(server, System.currentTimeMillis());
		Iterator<DropMatrixElement> iter = elements.iterator();
		while (iter.hasNext()) {
			DropMatrixElement element = iter.next();
			if (!openingStages.contains(element.getStageId()))
				iter.remove();
		}
	}

	private HttpHeaders generateLastModifiedHeadersFromLastUpdateMap(String key) {
		String lastModified = DateUtil.formatDate(new Date(LastUpdateTimeUtil.getLastUpdateTime(key)));
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.LAST_MODIFIED, lastModified);
		return headers;
	}

}
