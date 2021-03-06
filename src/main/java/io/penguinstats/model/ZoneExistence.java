package io.penguinstats.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZoneExistence implements Serializable {

	private static final long serialVersionUID = 1L;

	private Boolean exist;
	private Long openTime;
	private Long closeTime;

}
