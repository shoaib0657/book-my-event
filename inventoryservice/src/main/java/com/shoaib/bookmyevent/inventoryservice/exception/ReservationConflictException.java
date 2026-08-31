package com.shoaib.bookmyevent.inventoryservice.exception;

public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException(final String message) {
        super(message);
    }
}
