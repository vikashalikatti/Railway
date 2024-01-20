package com.project.railway.exception;

import java.io.IOException;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.project.railway.helper.ResponseStructure;

import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class MainExceptionHandler {

	@ExceptionHandler(ArithmeticException.class)
	public ResponseEntity<ResponseStructure<String>> handle(ArithmeticException exception) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(HttpStatus.BAD_REQUEST.value());
		structure.setMessage("There is an Arithmetic exception");
		structure.setData(exception.getMessage());

		return new ResponseEntity<ResponseStructure<String>>(structure, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(NoZeroException.class)
	public ResponseEntity<ResponseStructure<String>> handle(NoZeroException exception) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(HttpStatus.BAD_REQUEST.value());
		structure.setMessage("There is an NoZero exception");
		structure.setData(exception.getMessage());

		return new ResponseEntity<ResponseStructure<String>>(structure, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<ResponseStructure<String>> handle(IOException exception) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(HttpStatus.BAD_REQUEST.value());
		structure.setMessage("File Not Supported");
		structure.setData(exception.getMessage());

		return new ResponseEntity<ResponseStructure<String>>(structure, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(NullPointerException.class)
	public ResponseEntity<ResponseStructure<String>> handle(NullPointerException exception) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(HttpStatus.BAD_REQUEST.value());
		structure.setMessage("Object is NULL");
		structure.setData(exception.getMessage());

		return new ResponseEntity<ResponseStructure<String>>(structure, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ResponseStructure<String>> handle(ConstraintViolationException exception) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(HttpStatus.BAD_REQUEST.value());
		structure.setMessage("Constraint Violation");
		structure.setData(exception.getMessage());
		return new ResponseEntity<ResponseStructure<String>>(structure, HttpStatus.BAD_REQUEST);
	}
	@ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseStructure<String>> handleGeneralException(Exception ex) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(HttpStatus.BAD_REQUEST.value());
		structure.setData(ex.getMessage());
		return new ResponseEntity<ResponseStructure<String>>(structure, HttpStatus.BAD_REQUEST);
	}

}
