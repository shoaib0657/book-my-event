package com.shoaib.bookmyevent.inventoryservice.response;

import com.shoaib.bookmyevent.inventoryservice.entity.ReservationStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationResponse(
        UUID bookingId,
        Long eventId,
        Long ticketCount,
        ReservationStatus status,
        BigDecimal unitPrice,
        BigDecimal totalPrice) {
}
