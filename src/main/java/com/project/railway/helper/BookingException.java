package com.project.railway.helper;

import org.springframework.http.HttpStatus;

/** A booking rule was broken. Thrown inside a transaction, so everything it did is rolled back. */
public class BookingException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus status;

	public BookingException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
