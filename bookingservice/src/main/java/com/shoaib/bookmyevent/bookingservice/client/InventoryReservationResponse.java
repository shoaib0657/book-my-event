package com.shoaib.bookmyevent.bookingservice.client;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryReservationResponse(
        UUID bookingId,
        Long eventId,
        Long ticketCount,
        String status,
        BigDecimal unitPrice,
        BigDecimal totalPrice) {
}
