package com.medsync.notification.exception;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotificationNotFoundException.class)
	public ProblemDetail handleNotFound(NotificationNotFoundException exception) {

		return problem(HttpStatus.NOT_FOUND, "Notification not found", exception.getMessage());
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exception) {

		return problem(HttpStatus.CONFLICT, "Idempotency conflict", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {

		Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(error -> error.getField(),
						error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
						(first, second) -> first));

		ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
				"One or more request fields are invalid");

		detail.setProperty("fieldErrors", errors);
		return detail;
	}

	@ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class })
	public ProblemDetail handleBadRequest(Exception exception) {

		return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request contains invalid or unsupported values");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {

		return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with an existing database record");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception exception) {

		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
				"An unexpected error occurred while processing the request");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {

		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);

		problemDetail.setTitle(title);
		problemDetail.setType(URI.create("https://medsync.example.com/problems/" + status.value()));

		return problemDetail;
	}
}