package com.shoaib.bookmyevent.inventoryservice.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventInventoryResponse {
    private Long eventId;
    private String eventName;
    private Long totalCapacity;
    private Long remainingCapacity;
    private BigDecimal ticketPrice;
    private VenueInventoryResponse venue;
}
