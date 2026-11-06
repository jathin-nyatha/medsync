
package com.medsync.appointment.exception;

import java.time.Instant;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppointmentReferenceExceptionHandler {

	@ExceptionHandler(AppointmentReferenceValidationException.class)
	public ProblemDetail handleReferenceValidation(AppointmentReferenceValidationException exception) {

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());

		problem.setTitle(exception.getTitle());
		problem.setProperty("timestamp", Instant.now());

		return problem;
	}
}
