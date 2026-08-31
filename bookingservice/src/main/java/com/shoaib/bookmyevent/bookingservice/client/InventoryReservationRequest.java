package com.shoaib.bookmyevent.bookingservice.client;

import java.util.UUID;

public record InventoryReservationRequest(UUID bookingId, Long eventId, Long ticketCount) {
}
