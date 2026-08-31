package com.shoaib.bookmyevent.bookingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidRequest(final MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request body is invalid");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequest(final HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request body is invalid");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(final ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(BookingConflictException.class)
    ProblemDetail handleBookingConflict(final BookingConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Booking conflict", exception.getMessage());
    }

    @ExceptionHandler(InventoryServiceUnavailableException.class)
    ProblemDetail handleInventoryUnavailable(final InventoryServiceUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service unavailable", exception.getMessage());
    }

    private static ProblemDetail problem(final HttpStatus status, final String title, final String detail) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
