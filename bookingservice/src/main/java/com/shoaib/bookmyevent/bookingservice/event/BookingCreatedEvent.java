package com.shoaib.bookmyevent.bookingservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stable JSON contract published to {@code booking-created-v1} after Inventory confirms a reservation.
 * The topic name carries the contract version, so consumers do not depend on producer-specific Java type headers.
 */
public record BookingCreatedEvent(
        UUID bookingId,
        Long customerId,
        Long eventId,
        Long ticketCount,
        BigDecimal totalPrice) {
}
