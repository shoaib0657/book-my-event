package com.shoaib.bookmyevent.bookingservice.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(final String message) {
        super(message);
    }

    public InventoryServiceUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
