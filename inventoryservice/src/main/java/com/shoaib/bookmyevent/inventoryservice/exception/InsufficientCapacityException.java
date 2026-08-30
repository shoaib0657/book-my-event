package com.shoaib.bookmyevent.inventoryservice.exception;

public class InsufficientCapacityException extends RuntimeException {

    public InsufficientCapacityException(final String message) {
        super(message);
    }
}
