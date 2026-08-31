package com.shoaib.bookmyevent.inventoryservice.controller;

import com.shoaib.bookmyevent.inventoryservice.response.EventInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.request.CreateReservationRequest;
import com.shoaib.bookmyevent.inventoryservice.response.ReservationResponse;
import com.shoaib.bookmyevent.inventoryservice.response.VenueInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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

    @PostMapping("/inventory/reservations")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody final CreateReservationRequest request) {
        final var result = inventoryService.createReservation(request);

        // New reservations return 201; an identical idempotent replay returns the existing one with 200.
        return result.created()
                ? ResponseEntity.status(201).body(result.response())
                : ResponseEntity.ok(result.response());
    }

    // PUT models an idempotent state change; repeated releases remain no-ops in the service.
    @PutMapping("/inventory/reservations/{bookingId}/release")
    public ResponseEntity<Void> releaseReservation(@PathVariable final UUID bookingId) {
        inventoryService.releaseReservation(bookingId);
        return ResponseEntity.noContent().build();
    }
}
