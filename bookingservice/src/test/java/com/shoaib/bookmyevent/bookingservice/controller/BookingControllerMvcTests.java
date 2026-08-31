package com.shoaib.bookmyevent.bookingservice.controller;

import com.shoaib.bookmyevent.bookingservice.exception.BookingConflictException;
import com.shoaib.bookmyevent.bookingservice.exception.BookingEventPublicationException;
import com.shoaib.bookmyevent.bookingservice.exception.InventoryServiceUnavailableException;
import com.shoaib.bookmyevent.bookingservice.exception.ResourceNotFoundException;
import com.shoaib.bookmyevent.bookingservice.exception.ApiExceptionHandler;
import com.shoaib.bookmyevent.bookingservice.response.BookingResponse;
import com.shoaib.bookmyevent.bookingservice.response.BookingStatus;
import com.shoaib.bookmyevent.bookingservice.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingControllerMvcTests {

    private BookingService bookingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsAReservationAtThePluralBookingsRoute() throws Exception {
        final UUID bookingId = UUID.fromString("13c2a07c-2c04-416e-9e3f-a4c9e1a81b9e");
        when(bookingService.createBooking(any())).thenReturn(
                new BookingResponse(bookingId, BookingStatus.RESERVED, new BigDecimal("25.00")));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 41, "eventId": 8, "ticketCount": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.totalPrice").value(25.00));
    }

    @Test
    void rejectsZeroOrNullRequestFieldsAsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":null,\"eventId\":0,\"ticketCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsAMissingRequestBodyAsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsMalformedOrIncompatibleJsonAsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"not-a-number\",\"eventId\":8,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void mapsMissingCustomerToNotFoundProblemDetail() throws Exception {
        when(bookingService.createBooking(any())).thenThrow(new ResourceNotFoundException("Customer not found"));

        mockMvc.perform(validRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void mapsInventoryConflictToBookingConflictProblemDetail() throws Exception {
        when(bookingService.createBooking(any())).thenThrow(new BookingConflictException("No tickets available"));

        mockMvc.perform(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Booking conflict"));
    }

    @Test
    void mapsRequiredInventoryFailureToServiceUnavailableProblemDetail() throws Exception {
        when(bookingService.createBooking(any())).thenThrow(
                new InventoryServiceUnavailableException("Inventory service did not respond"));

        mockMvc.perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Inventory service unavailable"));
    }

    @Test
    void mapsKafkaPublicationFailureToServiceUnavailableProblemDetail() throws Exception {
        when(bookingService.createBooking(any())).thenThrow(new BookingEventPublicationException(
                "Booking event could not be published", new IllegalStateException("Kafka unavailable")));

        mockMvc.perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Booking service unavailable"))
                .andExpect(jsonPath("$.detail").value("Booking event could not be published"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return post("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":41,\"eventId\":8,\"ticketCount\":2}");
    }
}
