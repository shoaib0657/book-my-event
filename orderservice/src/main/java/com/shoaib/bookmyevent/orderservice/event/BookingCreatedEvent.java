package com.shoaib.bookmyevent.orderservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order Service's local representation of the JSON contract published to {@code booking-created-v1}.
 * Keeping this type local prevents Order Service from depending on Booking Service implementation classes.
 */
public record BookingCreatedEvent(
        UUID bookingId,
        Long customerId,
        Long eventId,
        Long ticketCount,
        BigDecimal totalPrice) {
}
