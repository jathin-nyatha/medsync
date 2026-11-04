
package com.medsync.doctor.exception;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Invalid value supplied for parameter: " + exception.getName());

		problem.setTitle("Invalid Request Parameter");
		problem.setProperty("timestamp", Instant.now());

		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(DoctorNotFoundException.class)
	public ProblemDetail handleDoctorNotFound(DoctorNotFoundException exception) {

		return createProblem(HttpStatus.NOT_FOUND, "Doctor Not Found", exception.getMessage());
	}

	@ExceptionHandler(DuplicateDoctorException.class)
	public ProblemDetail handleDuplicateDoctor(DuplicateDoctorException exception) {

		return createProblem(HttpStatus.CONFLICT, "Duplicate Doctor", exception.getMessage());
	}

	@ExceptionHandler(InvalidAuthUserException.class)
	public ProblemDetail handleInvalidAuthUser(InvalidAuthUserException exception) {

		return createProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid Auth User", exception.getMessage());
	}

	@ExceptionHandler(AuthServiceUnavailableException.class)
	public ProblemDetail handleAuthServiceUnavailable(AuthServiceUnavailableException exception) {

		return createProblem(HttpStatus.SERVICE_UNAVAILABLE, "Auth Service Unavailable",
				"Doctor registration cannot be completed because Auth Service is unavailable.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {

		return createProblem(HttpStatus.CONFLICT, "Data Integrity Violation",
				"The requested operation conflicts with existing data.");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidationErrors(MethodArgumentNotValidException exception) {

		Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(error -> error.getField(),
						error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
						(first, second) -> first));

		ProblemDetail problem = createProblem(HttpStatus.BAD_REQUEST, "Validation Failed",
				"One or more request fields contain invalid values.");

		problem.setProperty("errors", errors);

		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {

		return createProblem(HttpStatus.BAD_REQUEST, "Constraint Violation", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException exception) {

		return createProblem(HttpStatus.BAD_REQUEST, "Malformed Request",
				"Request body is missing, malformed, or contains invalid values.");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception exception) {

		return createProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				"An unexpected error occurred.");
	}

	private ProblemDetail createProblem(HttpStatus status, String title, String detail) {

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

		problem.setTitle(title);
		problem.setProperty("timestamp", Instant.now());

		return problem;
	}
}
