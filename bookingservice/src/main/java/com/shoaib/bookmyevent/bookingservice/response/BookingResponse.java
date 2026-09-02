package com.shoaib.bookmyevent.bookingservice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Confirmed customer booking")
public record BookingResponse(
        @Schema(description = "Confirmed booking identifier") UUID bookingId,
        @Schema(description = "Current booking status") BookingStatus status,
        @Schema(description = "Total price confirmed by Inventory Service") BigDecimal totalPrice) {
}
