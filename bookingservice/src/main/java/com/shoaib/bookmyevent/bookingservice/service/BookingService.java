package com.shoaib.bookmyevent.bookingservice.service;

import com.shoaib.bookmyevent.bookingservice.client.InventoryServiceClient;
import com.shoaib.bookmyevent.bookingservice.client.InventoryReservationResponse;
import com.shoaib.bookmyevent.bookingservice.event.BookingCreatedEvent;
import com.shoaib.bookmyevent.bookingservice.exception.BookingConflictException;
import com.shoaib.bookmyevent.bookingservice.exception.BookingEventPublicationException;
import com.shoaib.bookmyevent.bookingservice.exception.InventoryServiceUnavailableException;
import com.shoaib.bookmyevent.bookingservice.exception.ResourceNotFoundException;
import com.shoaib.bookmyevent.bookingservice.messaging.BookingEventPublisher;
import com.shoaib.bookmyevent.bookingservice.repository.CustomerRepository;
import com.shoaib.bookmyevent.bookingservice.request.BookingRequest;
import com.shoaib.bookmyevent.bookingservice.response.BookingResponse;
import com.shoaib.bookmyevent.bookingservice.response.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Coordinates the synchronous booking flow across Booking-owned customer data and Inventory Service.
 */
@Service
public class BookingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingService.class);

    private final CustomerRepository customerRepository;
    private final InventoryServiceClient inventoryServiceClient;
    private final BookingEventPublisher bookingEventPublisher;

    public BookingService(final CustomerRepository customerRepository,
                          final InventoryServiceClient inventoryServiceClient,
                          final BookingEventPublisher bookingEventPublisher) {
        this.customerRepository = customerRepository;
        this.inventoryServiceClient = inventoryServiceClient;
        this.bookingEventPublisher = bookingEventPublisher;
    }

    /**
     * Validates the customer, generates the booking identifier, and reserves the requested tickets.
     *
     * @param request customer, event, and ticket count supplied by the API caller
     * @return a reserved booking using Inventory Service's confirmed identifier and total price
     * @throws ResourceNotFoundException when the customer or requested event does not exist
     * @throws BookingConflictException when Inventory cannot reserve the requested tickets
     * @throws InventoryServiceUnavailableException when Inventory cannot provide a trustworthy response
     * @throws BookingEventPublicationException when the confirmed booking cannot be published to Kafka
     */
    public BookingResponse createBooking(final BookingRequest request) {
        // Fail before calling Inventory so an unknown local customer cannot hold capacity.
        if (customerRepository.findById(request.customerId()).isEmpty()) {
            throw new ResourceNotFoundException("Customer %d was not found".formatted(request.customerId()));
        }

        // Inventory treats this UUID as the idempotency key; reuse it if this reservation call is retried.
        final UUID bookingId = UUID.randomUUID();
        final InventoryReservationResponse reservation = inventoryServiceClient.reserve(
                bookingId, request.eventId(), request.ticketCount());

        final BookingCreatedEvent event = new BookingCreatedEvent(
                reservation.bookingId(),
                request.customerId(),
                reservation.eventId(),
                reservation.ticketCount(),
                reservation.totalPrice());

        try {
            bookingEventPublisher.publish(event);
        } catch (final BookingEventPublicationException publicationFailure) {
            compensateReservation(reservation.bookingId(), publicationFailure);
            throw publicationFailure;
        }

        // Inventory owns ticket pricing; both the event and response use its confirmed total.
        return new BookingResponse(reservation.bookingId(), BookingStatus.RESERVED, reservation.totalPrice());
    }

    private void compensateReservation(
            final UUID bookingId, final BookingEventPublicationException publicationFailure) {
        try {
            inventoryServiceClient.release(bookingId);
        } catch (final InventoryServiceUnavailableException releaseFailure) {
            // Keep the original Kafka failure visible while retaining evidence that manual recovery may be required.
            publicationFailure.addSuppressed(releaseFailure);
            LOGGER.error("Could not release inventory reservation {} after Kafka publication failed", bookingId,
                    releaseFailure);
        }
    }
}
