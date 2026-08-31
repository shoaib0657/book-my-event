package com.shoaib.bookmyevent.bookingservice.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingResponse(UUID bookingId, BookingStatus status, BigDecimal totalPrice) {
}
