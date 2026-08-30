package com.shoaib.bookmyevent.inventoryservice.service;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import com.shoaib.bookmyevent.inventoryservice.entity.Venue;
import com.shoaib.bookmyevent.inventoryservice.exception.InsufficientCapacityException;
import com.shoaib.bookmyevent.inventoryservice.exception.ResourceNotFoundException;
import com.shoaib.bookmyevent.inventoryservice.repository.EventRepository;
import com.shoaib.bookmyevent.inventoryservice.repository.VenueRepository;
import com.shoaib.bookmyevent.inventoryservice.response.EventInventoryResponse;
import com.shoaib.bookmyevent.inventoryservice.response.VenueInventoryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;

    @Autowired
    public InventoryService(final EventRepository eventRepository, final VenueRepository venueRepository) {
        this.eventRepository = eventRepository;
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

    @Transactional
    public void updateEventCapacity(final Long eventId, final Long ticketsBooked) {
        final int updatedEvents = eventRepository.decrementCapacityIfAvailable(eventId, ticketsBooked);
        if (updatedEvents == 1) {
            return;
        }

        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event %d was not found".formatted(eventId));
        }

        throw new InsufficientCapacityException(
                "Event %d does not have capacity for %d tickets".formatted(eventId, ticketsBooked));
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
}
