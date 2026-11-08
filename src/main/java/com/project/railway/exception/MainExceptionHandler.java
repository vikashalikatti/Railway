package com.project.railway.exception;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.project.railway.helper.ResponseStructure;

import jakarta.validation.ConstraintViolationException;

/**
 * Turns exceptions into the usual {status, message} reply.
 * Internal details (exception text, stack traces, SQL) are logged on the server and never sent
 * to the caller: they can reveal table names, file paths and library versions.
 */
@ControllerAdvice
public class MainExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(MainExceptionHandler.class);

	private static ResponseEntity<ResponseStructure<String>> reply(HttpStatus status, String message) {
		ResponseStructure<String> structure = new ResponseStructure<>();
		structure.setStatus(status.value());
		structure.setMessage(message);
		return new ResponseEntity<>(structure, status);
	}

	/* ------------------------------------------------- the caller's mistakes */

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ResponseStructure<String>> handleMissingRequestHeaderException(MissingRequestHeaderException exception) {
		return reply(HttpStatus.UNAUTHORIZED, "Log in to continue.");
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ResponseStructure<String>> handleBadCredentialsException(BadCredentialsException exception) {
		return reply(HttpStatus.BAD_REQUEST, "Wrong email or password");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ResponseStructure<String>> handleAccessDeniedException(AccessDeniedException exception) {
		return reply(HttpStatus.FORBIDDEN, "You don't have permission to do that.");
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class })
	public ResponseEntity<ResponseStructure<String>> badInput(Exception exception) {
		return reply(HttpStatus.BAD_REQUEST, "Some details are missing or not in the right format.");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ResponseStructure<String>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
		return reply(HttpStatus.BAD_REQUEST, "Photo must be 5 MB or smaller");
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ResponseStructure<String>> handleMultipartException(MultipartException exception) {
		return reply(HttpStatus.BAD_REQUEST, "The upload couldn't be read. Try again.");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ResponseStructure<String>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
		return reply(HttpStatus.METHOD_NOT_ALLOWED, "This request isn't supported.");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ResponseStructure<String>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException exception) {
		return reply(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This request isn't supported.");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ResponseStructure<String>> handleNoResourceFoundException(NoResourceFoundException exception) {
		return reply(HttpStatus.NOT_FOUND, "Not found.");
	}

	@ExceptionHandler(NoZeroException.class)
	public ResponseEntity<ResponseStructure<String>> handleNoZeroException(NoZeroException exception) {
		return reply(HttpStatus.BAD_REQUEST, "There is an NoZero exception");
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ResponseStructure<String>> handleConstraintViolationException(ConstraintViolationException exception) {
		return reply(HttpStatus.BAD_REQUEST, "Constraint Violation");
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<ResponseStructure<String>> handleIOException(IOException exception) {
		log.warn("File could not be read", exception);
		return reply(HttpStatus.BAD_REQUEST, "File Not Supported");
	}

	/* ------------------------------------------------------------- our bugs */

	@ExceptionHandler(ArithmeticException.class)
	public ResponseEntity<ResponseStructure<String>> handleArithmeticException(ArithmeticException exception) {
		log.error("Arithmetic error", exception);
		return reply(HttpStatus.INTERNAL_SERVER_ERROR, "There is an Arithmetic exception");
	}

	@ExceptionHandler(NullPointerException.class)
	public ResponseEntity<ResponseStructure<String>> handleNullPointerException(NullPointerException exception) {
		log.error("Missing value", exception);
		return reply(HttpStatus.INTERNAL_SERVER_ERROR, "Object is NULL");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ResponseStructure<String>> handleGeneralException(Exception ex) {
		log.error("Unhandled error", ex);
		return reply(HttpStatus.INTERNAL_SERVER_ERROR, "");
	}
}
