package com.shoaib.bookmyevent.orderservice.service;

import java.util.UUID;

/**
 * Signals that one booking UUID was reused for different immutable order data.
 */
public class ConflictingBookingEventException extends IllegalStateException {

    public ConflictingBookingEventException(final UUID bookingId) {
        super("Booking event conflicts with the stored order for booking " + bookingId);
    }
}
