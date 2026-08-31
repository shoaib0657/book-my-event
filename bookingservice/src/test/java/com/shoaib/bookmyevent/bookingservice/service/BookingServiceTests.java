package com.shoaib.bookmyevent.bookingservice.service;

import com.shoaib.bookmyevent.bookingservice.client.InventoryReservationResponse;
import com.shoaib.bookmyevent.bookingservice.client.InventoryServiceClient;
import com.shoaib.bookmyevent.bookingservice.entity.Customer;
import com.shoaib.bookmyevent.bookingservice.exception.ResourceNotFoundException;
import com.shoaib.bookmyevent.bookingservice.repository.CustomerRepository;
import com.shoaib.bookmyevent.bookingservice.request.BookingRequest;
import com.shoaib.bookmyevent.bookingservice.response.BookingResponse;
import com.shoaib.bookmyevent.bookingservice.response.BookingStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class BookingServiceTests {

    @Test
    void reservesInventoryForAnExistingCustomerAndReturnsTheConfirmedTotal() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingService service = new BookingService(customers, inventory);
        final BookingRequest request = new BookingRequest(41L, 8L, 2L);
        final ArgumentCaptor<UUID> bookingId = ArgumentCaptor.forClass(UUID.class);

        when(customers.findById(41L)).thenReturn(Optional.of(mock(Customer.class)));
        when(inventory.reserve(bookingId.capture(), eq(8L), eq(2L))).thenAnswer(invocation ->
                new InventoryReservationResponse(
                        invocation.getArgument(0), 8L, 2L, "RESERVED", new BigDecimal("12.50"), new BigDecimal("25.00")));

        final BookingResponse response = service.createBooking(request);

        assertNotNull(response.bookingId());
        assertEquals(bookingId.getValue(), response.bookingId());
        assertEquals(BookingStatus.RESERVED, response.status());
        assertEquals(new BigDecimal("25.00"), response.totalPrice());
        verify(inventory).reserve(bookingId.getValue(), 8L, 2L);
    }

    @Test
    void rejectsAnUnknownCustomerBeforeCallingInventory() {
        final CustomerRepository customers = mock(CustomerRepository.class);
        final InventoryServiceClient inventory = mock(InventoryServiceClient.class);
        final BookingService service = new BookingService(customers, inventory);

        when(customers.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createBooking(new BookingRequest(999L, 8L, 2L)));

        verify(inventory, never()).reserve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
