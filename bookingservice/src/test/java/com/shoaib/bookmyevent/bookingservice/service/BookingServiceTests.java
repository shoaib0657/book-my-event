package com.shoaib.bookmyevent.bookingservice.service;

import com.shoaib.bookmyevent.bookingservice.client.InventoryReservationResponse;
import com.shoaib.bookmyevent.bookingservice.client.InventoryServiceClient;
import com.shoaib.bookmyevent.bookingservice.entity.Customer;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTests {

    @Test
    void reservesInventoryForAnExistingCustomerAndReturnsTheConfirmedTotal() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        final BookingService service = new BookingService(customers, inventory, publisher);
        final BookingRequest request = new BookingRequest(41L, 8L, 2L);
        final ArgumentCaptor<BookingCreatedEvent> publishedEvent = ArgumentCaptor.forClass(BookingCreatedEvent.class);

        when(customers.findById(41L)).thenReturn(Optional.of(mock(Customer.class)));
        when(inventory.reserve(any(UUID.class), eq(8L), eq(2L))).thenAnswer(invocation ->
                new InventoryReservationResponse(
                        invocation.getArgument(0), 8L, 2L, "RESERVED", new BigDecimal("12.50"), new BigDecimal("25.00")));

        final BookingResponse response = service.createBooking(request);

        assertNotNull(response.bookingId());
        assertEquals(BookingStatus.RESERVED, response.status());
        assertEquals(new BigDecimal("25.00"), response.totalPrice());

        final InOrder flow = inOrder(customers, inventory, publisher);
        flow.verify(customers).findById(41L);
        flow.verify(inventory).reserve(response.bookingId(), 8L, 2L);
        flow.verify(publisher).publish(publishedEvent.capture());

        assertEquals(response.bookingId(), publishedEvent.getValue().bookingId());
        assertEquals(41L, publishedEvent.getValue().customerId());
        assertEquals(8L, publishedEvent.getValue().eventId());
        assertEquals(2L, publishedEvent.getValue().ticketCount());
        assertEquals(new BigDecimal("25.00"), publishedEvent.getValue().totalPrice());
    }

    @Test
    void rejectsAnUnknownCustomerBeforeCallingInventory() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        final BookingService service = new BookingService(customers, inventory, publisher);

        when(customers.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createBooking(new BookingRequest(999L, 8L, 2L)));

        verify(inventory, never()).reserve(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void doesNotPublishWhenInventoryCannotReserveTheTickets() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        final BookingService service = new BookingService(customers, inventory, publisher);

        when(customers.findById(41L)).thenReturn(Optional.of(mock(Customer.class)));
        when(inventory.reserve(any(UUID.class), eq(8L), eq(2L)))
                .thenThrow(new BookingConflictException("No tickets available"));

        assertThrows(BookingConflictException.class,
                () -> service.createBooking(new BookingRequest(41L, 8L, 2L)));

        verify(publisher, never()).publish(any());
        verify(inventory, never()).release(any());
    }

    @Test
    void releasesInventoryAndPreservesThePublicationFailureWhenKafkaFails() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        final BookingService service = new BookingService(customers, inventory, publisher);
        final BookingEventPublicationException publicationFailure = new BookingEventPublicationException(
                "Booking event could not be published", new IllegalStateException("Kafka unavailable"));

        when(customers.findById(41L)).thenReturn(Optional.of(mock(Customer.class)));
        when(inventory.reserve(any(UUID.class), eq(8L), eq(2L))).thenAnswer(invocation ->
                new InventoryReservationResponse(
                        invocation.getArgument(0), 8L, 2L, "RESERVED", new BigDecimal("12.50"), new BigDecimal("25.00")));
        doThrow(publicationFailure).when(publisher).publish(any());

        final BookingEventPublicationException thrown = assertThrows(
                BookingEventPublicationException.class,
                () -> service.createBooking(new BookingRequest(41L, 8L, 2L)));

        final ArgumentCaptor<BookingCreatedEvent> attemptedEvent = ArgumentCaptor.forClass(BookingCreatedEvent.class);
        verify(publisher).publish(attemptedEvent.capture());
        verify(inventory).release(attemptedEvent.getValue().bookingId());
        assertSame(publicationFailure, thrown);
    }

    @Test
    void attachesACompensationFailureWithoutHidingTheKafkaFailure() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        final BookingService service = new BookingService(customers, inventory, publisher);
        final BookingEventPublicationException publicationFailure = new BookingEventPublicationException(
                "Booking event could not be published", new IllegalStateException("Kafka unavailable"));
        final InventoryServiceUnavailableException releaseFailure = new InventoryServiceUnavailableException(
                "Inventory reservation could not be released");

        when(customers.findById(41L)).thenReturn(Optional.of(mock(Customer.class)));
        when(inventory.reserve(any(UUID.class), eq(8L), eq(2L))).thenAnswer(invocation ->
                new InventoryReservationResponse(
                        invocation.getArgument(0), 8L, 2L, "RESERVED", new BigDecimal("12.50"), new BigDecimal("25.00")));
        doThrow(publicationFailure).when(publisher).publish(any());
        doThrow(releaseFailure).when(inventory).release(any());

        final BookingEventPublicationException thrown = assertThrows(
                BookingEventPublicationException.class,
                () -> service.createBooking(new BookingRequest(41L, 8L, 2L)));

        assertSame(publicationFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(releaseFailure, thrown.getSuppressed()[0]);
    }
}
