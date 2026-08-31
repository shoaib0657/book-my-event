package com.shoaib.bookmyevent.inventoryservice.service;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import com.shoaib.bookmyevent.inventoryservice.entity.Reservation;
import com.shoaib.bookmyevent.inventoryservice.entity.ReservationStatus;
import com.shoaib.bookmyevent.inventoryservice.entity.Venue;
import com.shoaib.bookmyevent.inventoryservice.exception.InsufficientCapacityException;
import com.shoaib.bookmyevent.inventoryservice.exception.ReservationConflictException;
import com.shoaib.bookmyevent.inventoryservice.exception.ResourceNotFoundException;
import com.shoaib.bookmyevent.inventoryservice.repository.EventRepository;
import com.shoaib.bookmyevent.inventoryservice.repository.ReservationRepository;
import com.shoaib.bookmyevent.inventoryservice.repository.VenueRepository;
import com.shoaib.bookmyevent.inventoryservice.request.CreateReservationRequest;
import com.shoaib.bookmyevent.inventoryservice.response.EventInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.response.ReservationResponse;
import com.shoaib.bookmyevent.inventoryservice.response.VenueInventoryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns inventory read models and the transactional invariants for reserving and releasing event capacity.
 */
@Service
public class InventoryService {

    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final VenueRepository venueRepository;

    @Autowired
    public InventoryService(
            final EventRepository eventRepository,
            final ReservationRepository reservationRepository,
            final VenueRepository venueRepository) {
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.venueRepository = venueRepository;
    }

    @Transactional(readOnly = true)
    public List<EventInventoryResponse> getAllEvents() {
        final List<Event> events = eventRepository.findAllByOrderByIdAsc();

        return events.stream()
                .map(this::toEventInventoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VenueInventoryResponse getVenueInformation(final Long venueId) {
        final Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue %d was not found".formatted(venueId)));

        return toVenueInventoryResponse(venue);
    }

    @Transactional(readOnly = true)
    public EventInventoryResponse getEventInventory(final Long eventId) {
        final Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event %d was not found".formatted(eventId)));
        return toEventInventoryResponse(event);
    }

    /**
     * Creates a reservation or safely replays an identical request using the booking UUID as an idempotency key.
     *
     * <p>A new reservation captures the current price and decrements capacity once. An identical active reservation
     * is returned without another decrement, while changed details or reuse after release are rejected.</p>
     *
     * @param request booking ID, event ID, and ticket count to reserve
     * @return the reservation and whether this call created it
     * @throws ResourceNotFoundException when the event does not exist
     * @throws InsufficientCapacityException when the event cannot satisfy the ticket count
     * @throws ReservationConflictException when the booking UUID conflicts with an existing reservation
     */
    @Transactional
    public ReservationResult createReservation(final CreateReservationRequest request) {
        // Fast path for completed retries; the unique-key insert below still handles concurrent first requests.
        final var existing = reservationRepository.findByBookingId(request.bookingId());
        if (existing.isPresent()) {
            return replayExistingReservation(existing.get(), request);
        }

        // The event lock protects capacity; the duplicate-key insert sets a
        // connection-local created flag so cross-event replays never need a failed insert.
        final Event event = eventRepository.findByIdForUpdate(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event %d was not found".formatted(request.eventId())));
        final var unitPrice = event.getTicketPrice();
        final var totalPrice = unitPrice.multiply(java.math.BigDecimal.valueOf(request.ticketCount()));
        reservationRepository.insertIfAbsent(
                request.bookingId().toString(), request.eventId(), request.ticketCount(), unitPrice, totalPrice);
        final Reservation reservation = reservationRepository.findByBookingIdForUpdate(request.bookingId())
                .orElseThrow(() -> new IllegalStateException("Reservation insert did not produce a reservation"));
        if (reservationRepository.reservationWasCreated() == 0) {
            return replayExistingReservation(reservation, request);
        }

        // Throwing here also rolls back the provisional reservation inserted in this transaction.
        if (event.getLeftCapacity() < request.ticketCount()) {
            throw new InsufficientCapacityException(
                    "Event %d does not have capacity for %d tickets"
                            .formatted(request.eventId(), request.ticketCount()));
        }

        event.setLeftCapacity(event.getLeftCapacity() - request.ticketCount());
        return new ReservationResult(toReservationResponse(reservation), true);
    }

    /**
     * Releases a reservation and restores its capacity at most once.
     *
     * <p>Repeating a successful release is a no-op because only the {@code RESERVED -> RELEASED} transition changes
     * capacity.</p>
     *
     * @param bookingId booking-owned reservation identifier
     * @throws ResourceNotFoundException when the reservation or its event does not exist
     */
    @Transactional
    public void releaseReservation(final UUID bookingId) {
        // Discover the event first, then lock event before reservation to match creation's lock order.
        final Reservation initial = reservationRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation %s was not found".formatted(bookingId)));
        final Event event = eventRepository.findByIdForUpdate(initial.getEvent().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event %d was not found".formatted(initial.getEvent().getId())));
        final Reservation reservation = reservationRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation %s was not found".formatted(bookingId)));

        // Only this state transition restores capacity, making repeated release requests safe.
        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            reservation.setStatus(ReservationStatus.RELEASED);
            event.setLeftCapacity(event.getLeftCapacity() + reservation.getTicketCount());
        }
    }

    private ReservationResult replayExistingReservation(
            final Reservation reservation, final CreateReservationRequest request) {
        // One booking UUID represents one immutable active reservation.
        // Changed details or reuse after release are conflicts.
        if (reservation.getStatus() == ReservationStatus.RELEASED
                || !reservation.getEvent().getId().equals(request.eventId())
                || !reservation.getTicketCount().equals(request.ticketCount())) {
            throw new ReservationConflictException(
                    "Booking %s conflicts with its existing reservation".formatted(request.bookingId()));
        }
        return new ReservationResult(toReservationResponse(reservation), false);
    }

    private ReservationResponse toReservationResponse(final Reservation reservation) {
        return new ReservationResponse(
                reservation.getBookingId(),
                reservation.getEvent().getId(),
                reservation.getTicketCount(),
                reservation.getStatus(),
                reservation.getUnitPrice(),
                reservation.getTotalPrice());
    }

    private EventInventoryResponse toEventInventoryResponse(final Event event) {
        return EventInventoryResponse.builder()
                .eventId(event.getId())
                .eventName(event.getName())
                .totalCapacity(event.getTotalCapacity())
                .remainingCapacity(event.getLeftCapacity())
                .ticketPrice(event.getTicketPrice())
                .venue(toVenueInventoryResponse(event.getVenue()))
                .build();
    }

    private VenueInventoryResponse toVenueInventoryResponse(final Venue venue) {
        return VenueInventoryResponse.builder()
                .venueId(venue.getId())
                .venueName(venue.getName())
                .address(venue.getAddress())
                .totalCapacity(venue.getTotalCapacity())
                .build();
    }

    /**
     * Reservation outcome used by the web layer to select creation or replay HTTP semantics.
     *
     * @param response public reservation representation
     * @param created {@code true} for a newly inserted reservation; {@code false} for an identical replay
     */
    public record ReservationResult(ReservationResponse response, boolean created) {
    }
}
