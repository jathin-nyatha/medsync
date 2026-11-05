
package com.medsync.patient.exception;

import java.net.URI;
import java.time.Instant;
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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// Patient profile not found: 404
	@ExceptionHandler(PatientNotFoundException.class)
	public ProblemDetail handlePatientNotFound(PatientNotFoundException exception) {

		return createProblem(HttpStatus.NOT_FOUND, "Patient Not Found", exception.getMessage());
	}

	// Duplicate patient profile: 409
	@ExceptionHandler(DuplicatePatientProfileException.class)
	public ProblemDetail handleDuplicatePatient(DuplicatePatientProfileException exception) {

		return createProblem(HttpStatus.CONFLICT, "Duplicate Patient Profile", exception.getMessage());
	}

	// Invalid pagination or sorting: 400
	@ExceptionHandler(InvalidPaginationException.class)
	public ProblemDetail handleInvalidPagination(InvalidPaginationException exception) {

		return createProblem(HttpStatus.BAD_REQUEST, "Invalid Pagination Parameters", exception.getMessage());
	}

	// DTO validation failures: 400
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

	// Bean validation failures on method parameters: 400
	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {

		Map<String, String> errors = exception.getConstraintViolations().stream()
				.collect(Collectors.toMap(violation -> violation.getPropertyPath().toString(),
						violation -> violation.getMessage(), (first, second) -> first));

		ProblemDetail problem = createProblem(HttpStatus.BAD_REQUEST, "Constraint Violation",
				"One or more parameters contain invalid values.");

		problem.setProperty("errors", errors);

		return problem;
	}

	// Invalid path variable or query parameter type: 400
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {

		return createProblem(HttpStatus.BAD_REQUEST, "Invalid Request Parameter",
				"Invalid value supplied for parameter: " + exception.getName());
	}

	// Missing required request parameter: 400
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {

		return createProblem(HttpStatus.BAD_REQUEST, "Missing Request Parameter",
				"Required parameter is missing: " + exception.getParameterName());
	}

	// Malformed JSON or invalid enum value: 400
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException exception) {

		return createProblem(HttpStatus.BAD_REQUEST, "Malformed Request",
				"Request body is missing, malformed, or contains invalid values.");
	}

	// Unsupported HTTP method: 405
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {

		return createProblem(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
				"The requested HTTP method is not supported for this endpoint.");
	}

	// Database constraint violation: 409
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {

		log.warn("Database integrity constraint violation", exception);

		return createProblem(HttpStatus.CONFLICT, "Data Integrity Violation",
				"The requested operation conflicts with existing data.");
	}

	// Unexpected errors: 500
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception exception) {

		log.error("Unexpected error while processing Patient Service request", exception);

		return createProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				"An unexpected error occurred.");
	}

	// Common ProblemDetail factory
	private ProblemDetail createProblem(HttpStatus status, String title, String detail) {

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

		problem.setTitle(title);
		problem.setProperty("timestamp", Instant.now());

		return problem;
	}

	@ExceptionHandler(PatientAccessDeniedException.class)
	public ProblemDetail handlePatientAccessDenied(PatientAccessDeniedException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(AppointmentServiceUnavailableException.class)
	public ProblemDetail handleAppointmentServiceUnavailable(AppointmentServiceUnavailableException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Patient access verification is temporarily unavailable");
	}

	@ExceptionHandler(AppointmentServiceIntegrationException.class)
	public ProblemDetail handleAppointmentServiceIntegration(AppointmentServiceIntegrationException exception) {

		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"Patient access verification failed due to an upstream service error");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException exception,
			HttpServletRequest request) {

		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"You do not have permission to access this resource.");

		problemDetail.setTitle("Forbidden");
		problemDetail.setInstance(URI.create(request.getRequestURI()));
		problemDetail.setProperty("timestamp", Instant.now());

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
	}
}
