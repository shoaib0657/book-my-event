package com.shoaib.bookmyevent.inventoryservice.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID bookingId,
        @NotNull @Positive Long eventId,
        @NotNull @Positive Long ticketCount) {
}
