package com.project.railway.helper;

import lombok.Data;

@Data
public class ResponseStructure<T> {
	int status;
	String data;
	String message;
	T data2;
}
