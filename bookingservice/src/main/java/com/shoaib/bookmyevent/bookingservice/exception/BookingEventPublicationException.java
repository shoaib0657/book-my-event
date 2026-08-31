package com.shoaib.bookmyevent.bookingservice.exception;

/**
 * Indicates that Booking Service could not durably hand a confirmed booking to Kafka.
 */
public class BookingEventPublicationException extends RuntimeException {

    public BookingEventPublicationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
