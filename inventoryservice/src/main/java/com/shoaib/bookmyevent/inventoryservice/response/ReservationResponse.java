package com.shoaib.bookmyevent.inventoryservice.response;

import com.shoaib.bookmyevent.inventoryservice.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Confirmed inventory reservation")
public record ReservationResponse(
        @Schema(description = "Reservation booking identifier") UUID bookingId,
        @Schema(description = "Reserved event identifier") Long eventId,
        @Schema(description = "Reserved ticket quantity") Long ticketCount,
        @Schema(description = "Current reservation status") ReservationStatus status,
        @Schema(description = "Price for one ticket") BigDecimal unitPrice,
        @Schema(description = "Total price of all reserved tickets") BigDecimal totalPrice) {
}
