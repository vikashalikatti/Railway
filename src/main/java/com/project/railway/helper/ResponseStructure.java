package com.project.railway.helper;

import java.util.List;

import com.project.railway.dto.Station;

import lombok.Data;

@Data
public class ResponseStructure<T> {
	int status;
	String data;
	String message;
	T data2;
	List<Station> listStation;
}
