package io.penguinstats.controller.v2.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.penguinstats.enums.ErrorCode;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import springfox.documentation.annotations.ApiIgnore;

@ApiIgnore
@NoArgsConstructor
public class ErrorResponseWrapper<T> {
  @ApiModelProperty(name = "code", value = "status code", required = false, position = -2)
  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Integer code;

  @ApiModelProperty(name = "message", value = "response message", required = false, position = -1)
  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String message;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Object error;

  public ErrorResponseWrapper(ErrorCode errorCode, String message) {
    this.code = errorCode.getValue();
    this.message = message;
  }

  public ErrorResponseWrapper(Integer code, String message) {
    this.code = code;
    this.message = message;
  }
}
