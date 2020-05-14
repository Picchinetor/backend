package io.penguinstats.controller.v2.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import io.swagger.annotations.Api;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

@RestController("formulaController_v2")
@RequestMapping("/api/v2/formula")
@Api(tags = {"Formula"})
public class FormulaController {

	private static Logger logger = LogManager.getLogger(FormulaController.class);

	@ApiOperation(value = "Get all Formulas",
			notes = "Get the synthesis conversion formulas between different items.")
	@GetMapping(produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> getFormula() {
		Resource resource = new ClassPathResource("json/formula.json");
		try {
			File sourceFile = resource.getFile();
			BufferedReader reader = new BufferedReader(new FileReader(sourceFile));
			StringBuilder builder = new StringBuilder();
			String currentLine = reader.readLine();
			while (currentLine != null) {
				builder.append(currentLine).append("\n");
				currentLine = reader.readLine();
			}
			reader.close();
			return new ResponseEntity<>(builder.toString(), HttpStatus.OK);
		} catch (IOException e) {
			logger.error("Error in getFormula: ", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

	}

}
