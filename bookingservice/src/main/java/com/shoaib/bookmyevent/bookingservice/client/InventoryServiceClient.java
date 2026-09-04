package com.shoaib.bookmyevent.bookingservice.client;

import com.shoaib.bookmyevent.bookingservice.exception.BookingConflictException;
import com.shoaib.bookmyevent.bookingservice.exception.InventoryServiceUnavailableException;
import com.shoaib.bookmyevent.bookingservice.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Booking-side HTTP adapter for Inventory Service reservation operations.
 *
 * <p>The adapter translates Inventory HTTP failures into Booking-domain exceptions and rejects malformed successful
 * responses before they can become confirmed bookings.</p>
 */
@Service
public class InventoryServiceClient {

    private final RestClient restClient;
    private final CircuitBreaker reservationCircuitBreaker;

    public InventoryServiceClient(final RestClient.Builder restClientBuilder,
                                  @Value("${inventory.service.url}") final String inventoryServiceUrl,
                                  @Qualifier("inventoryServiceCircuitBreaker")
                                  final CircuitBreaker reservationCircuitBreaker) {
        this.restClient = restClientBuilder.baseUrl(inventoryServiceUrl).build();
        this.reservationCircuitBreaker = reservationCircuitBreaker;
    }

    /**
     * Requests an idempotent Inventory reservation for a Booking-owned UUID.
     *
     * @param bookingId idempotency key shared with Inventory Service
     * @param eventId event to reserve
     * @param ticketCount number of tickets requested
     * @return the validated reservation confirmed by Inventory Service
     * @throws ResourceNotFoundException when Inventory reports that the event does not exist
     * @throws BookingConflictException when Inventory rejects the requested reservation
     * @throws InventoryServiceUnavailableException when the call fails or the response contract is invalid
     */
    public InventoryReservationResponse reserve(
            final UUID bookingId, final Long eventId, final Long ticketCount) {
        final InventoryReservationRequest request = new InventoryReservationRequest(bookingId, eventId, ticketCount);
        return reservationCircuitBreaker.run(
                () -> reserveFromInventory(request),
                InventoryServiceClient::throwReservationFailure);
    }

    private InventoryReservationResponse reserveFromInventory(final InventoryReservationRequest request) {
        // Keep Inventory's HTTP details here so BookingService only deals with domain failures.
        try {
            final InventoryReservationResponse response = restClient.post()
                    .uri("/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (clientRequest, clientResponse) -> {
                                throw new ResourceNotFoundException("Requested inventory event was not found");
                            })
                    .onStatus(status -> status.value() == 409,
                            (clientRequest, clientResponse) -> {
                                throw new BookingConflictException("Inventory could not reserve the requested tickets");
                            })
                    .body(InventoryReservationResponse.class);

            // A successful status is not enough; mismatched data could confirm the wrong reservation.
            validateReservationResponse(response, request);
            return response;
        } catch (final ResourceNotFoundException | BookingConflictException exception) {
            throw exception;
        } catch (final RestClientException exception) {
            throw new InventoryServiceUnavailableException("Inventory service request failed", exception);
        }
    }

    private static InventoryReservationResponse throwReservationFailure(final Throwable failure) {
        if (failure instanceof ResourceNotFoundException resourceNotFound) {
            throw resourceNotFound;
        }
        if (failure instanceof BookingConflictException bookingConflict) {
            throw bookingConflict;
        }
        if (failure instanceof InventoryServiceUnavailableException inventoryUnavailable) {
            throw inventoryUnavailable;
        }
        throw new InventoryServiceUnavailableException("Inventory service is temporarily unavailable", failure);
    }

    /**
     * Releases a previously confirmed reservation during booking-flow compensation.
     *
     * @param bookingId identifier of the reservation to release
     * @throws InventoryServiceUnavailableException when Inventory cannot confirm the release
     */
    public void release(final UUID bookingId) {
        try {
            restClient.put()
                    .uri("/reservations/{bookingId}/release", bookingId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (final RestClientException exception) {
            throw new InventoryServiceUnavailableException("Inventory reservation could not be released", exception);
        }
    }

    private static void validateReservationResponse(
            final InventoryReservationResponse response, final InventoryReservationRequest request) {
        if (response == null
                || !request.bookingId().equals(response.bookingId())
                || !request.eventId().equals(response.eventId())
                || !request.ticketCount().equals(response.ticketCount())
                || !"RESERVED".equals(response.status())
                || isNegativeOrNull(response.unitPrice())
                || isNegativeOrNull(response.totalPrice())) {
            throw new InventoryServiceUnavailableException("Inventory service returned an invalid reservation response");
        }
    }

    private static boolean isNegativeOrNull(final BigDecimal price) {
        return price == null || price.signum() < 0;
    }
}
