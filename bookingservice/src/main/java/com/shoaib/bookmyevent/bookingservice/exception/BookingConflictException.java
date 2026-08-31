package com.shoaib.bookmyevent.bookingservice.exception;

public class BookingConflictException extends RuntimeException {

    public BookingConflictException(final String message) {
        super(message);
    }
}
