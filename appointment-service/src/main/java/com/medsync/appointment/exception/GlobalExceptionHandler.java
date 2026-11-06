package com.medsync.appointment.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AppointmentNotFoundException.class)
	public ProblemDetail handleAppointmentNotFound(AppointmentNotFoundException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(AppointmentConflictException.class)
	public ProblemDetail handleAppointmentConflict(AppointmentConflictException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(InvalidAppointmentOperationException.class)
	public ProblemDetail handleInvalidOperation(InvalidAppointmentOperationException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(InvalidAppointmentTimeException.class)
	public ProblemDetail handleInvalidAppointmentTime(InvalidAppointmentTimeException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(AppointmentAccessDeniedException.class)
	public ProblemDetail handleAppointmentAccessDenied(AppointmentAccessDeniedException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleSpringAccessDenied(AccessDeniedException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"You do not have permission to perform this operation.");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handleValidationErrors(MethodArgumentNotValidException exception) {

		Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(FieldError::getField,
						error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
						(first, second) -> first));

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");

		problem.setProperty("errors", errors);

		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Invalid value supplied for parameter: " + exception.getName());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Request body is missing or contains invalid JSON.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The requested operation violates a database constraint.");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception exception) {

		log.error("Unexpected error in Appointment Service", exception);

		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
	}

	@ExceptionHandler(InvalidAppointmentRequestException.class)
	public ProblemDetail handleInvalidAppointmentRequest(InvalidAppointmentRequestException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}
}