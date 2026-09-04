package com.shoaib.bookmyevent.bookingservice.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Booking request")
public record BookingRequest(
        @Schema(description = "Customer placing the booking", example = "41") @NotNull @Positive Long customerId,
        @Schema(description = "Event to book", example = "8") @NotNull @Positive Long eventId,
        @Schema(description = "Number of tickets to book", example = "2", maximum = "100")
        @NotNull @Positive @Max(100) Long ticketCount) {
}
