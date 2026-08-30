package com.shoaib.bookmyevent.inventoryservice.controller;

import com.shoaib.bookmyevent.inventoryservice.response.EventInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.response.VenueInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(final InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory/events")
    public List<EventInventoryResponse> getAllEvents() {
        return inventoryService.getAllEvents();
    }

    @GetMapping("/inventory/venue/{venueId}")
    public VenueInventoryResponse getVenue(
            @PathVariable("venueId") @Positive Long venueId) {
        return inventoryService.getVenueInformation(venueId);
    }

    @GetMapping("/inventory/event/{eventId}")
    public EventInventoryResponse getEvent(
            @PathVariable("eventId") @Positive Long eventId) {
        return inventoryService.getEventInventory(eventId);
    }

    @PutMapping("/inventory/event/{eventId}/capacity/{ticketsBooked}")
    public ResponseEntity<Void> updateEventCapacity(
            @PathVariable("eventId") @Positive final Long eventId,
            @PathVariable("ticketsBooked") @Positive final Long ticketsBooked) {
        inventoryService.updateEventCapacity(eventId, ticketsBooked);
        return ResponseEntity.ok().build();
    }
}
