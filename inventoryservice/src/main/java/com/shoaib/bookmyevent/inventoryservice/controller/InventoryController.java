package com.shoaib.bookmyevent.inventoryservice.controller;

import com.shoaib.bookmyevent.inventoryservice.response.EventInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.request.CreateReservationRequest;
import com.shoaib.bookmyevent.inventoryservice.response.ReservationResponse;
import com.shoaib.bookmyevent.inventoryservice.response.VenueInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Inventory", description = "Event inventory and reservation operations")
public class InventoryController {

    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(final InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory/events")
    @Operation(summary = "List event inventory")
    @ApiResponse(responseCode = "200", description = "Event inventory returned")
    public List<EventInventoryResponse> getAllEvents() {
        return inventoryService.getAllEvents();
    }

    @GetMapping("/inventory/venue/{venueId}")
    @Operation(summary = "Get venue inventory")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Venue inventory returned"),
            @ApiResponse(responseCode = "400", description = "Venue ID is invalid"),
            @ApiResponse(responseCode = "404", description = "Venue was not found")
    })
    public VenueInventoryResponse getVenue(
            @PathVariable("venueId") @Positive Long venueId) {
        return inventoryService.getVenueInformation(venueId);
    }

    @GetMapping("/inventory/event/{eventId}")
    @Operation(summary = "Get event inventory")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event inventory returned"),
            @ApiResponse(responseCode = "400", description = "Event ID is invalid"),
            @ApiResponse(responseCode = "404", description = "Event was not found")
    })
    public EventInventoryResponse getEvent(
            @PathVariable("eventId") @Positive Long eventId) {
        return inventoryService.getEventInventory(eventId);
    }

    @PostMapping("/inventory/reservations")
    @Operation(summary = "Create an inventory reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing idempotent reservation returned"),
            @ApiResponse(responseCode = "201", description = "Reservation created"),
            @ApiResponse(responseCode = "400", description = "Reservation request is invalid"),
            @ApiResponse(responseCode = "404", description = "Event was not found"),
            @ApiResponse(responseCode = "409", description = "Reservation conflicts or capacity is unavailable")
    })
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
    @Operation(summary = "Release an inventory reservation")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reservation released or already released"),
            @ApiResponse(responseCode = "400", description = "Booking ID is invalid"),
            @ApiResponse(responseCode = "404", description = "Reservation was not found")
    })
    public ResponseEntity<Void> releaseReservation(@PathVariable final UUID bookingId) {
        inventoryService.releaseReservation(bookingId);
        return ResponseEntity.noContent().build();
    }
}
