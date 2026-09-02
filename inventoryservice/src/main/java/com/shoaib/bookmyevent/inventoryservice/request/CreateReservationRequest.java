package com.shoaib.bookmyevent.inventoryservice.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(description = "Inventory reservation request")
public record CreateReservationRequest(
        @Schema(description = "Idempotency key supplied by Booking Service") @NotNull UUID bookingId,
        @Schema(description = "Event to reserve tickets for", example = "3") @NotNull @Positive Long eventId,
        @Schema(description = "Number of tickets to reserve", example = "2") @NotNull @Positive Long ticketCount) {
}
