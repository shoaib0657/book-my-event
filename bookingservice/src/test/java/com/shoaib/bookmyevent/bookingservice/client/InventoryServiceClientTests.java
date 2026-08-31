package com.shoaib.bookmyevent.bookingservice.client;

import com.shoaib.bookmyevent.bookingservice.exception.BookingConflictException;
import com.shoaib.bookmyevent.bookingservice.exception.InventoryServiceUnavailableException;
import com.shoaib.bookmyevent.bookingservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class InventoryServiceClientTests {

    private static final String BASE_URL = "http://inventory.test/api/v1/inventory";
    private static final UUID BOOKING_ID = UUID.fromString("13c2a07c-2c04-416e-9e3f-a4c9e1a81b9e");

    private InventoryServiceClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        final RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InventoryServiceClient(builder, BASE_URL);
    }

    @Test
    void postsTheExactReservationPayloadAndDeserializesTheResponse() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"bookingId":"13c2a07c-2c04-416e-9e3f-a4c9e1a81b9e","eventId":8,"ticketCount":2}
                        """))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(reservationResponse(BOOKING_ID, 8L, 2L, "RESERVED")));

        final InventoryReservationResponse response = client.reserve(BOOKING_ID, 8L, 2L);

        assertEquals(BOOKING_ID, response.bookingId());
        assertEquals(8L, response.eventId());
        assertEquals(2L, response.ticketCount());
        assertEquals("RESERVED", response.status());
        assertEquals(new BigDecimal("12.50"), response.unitPrice());
        assertEquals(new BigDecimal("25.00"), response.totalPrice());
        server.verify();
    }

    @Test
    void mapsInventoryNotFoundToBookingNotFound() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> client.reserve(BOOKING_ID, 8L, 2L));
    }

    @Test
    void mapsInventoryConflictToBookingConflict() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));

        assertThrows(BookingConflictException.class, () -> client.reserve(BOOKING_ID, 8L, 2L));
    }

    @Test
    void mapsServerErrorsToRequiredServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/reservations")).andRespond(withServerError());

        assertThrows(InventoryServiceUnavailableException.class, () -> client.reserve(BOOKING_ID, 8L, 2L));
    }

    @Test
    void rejectsAnEmptyReservationResponse() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(InventoryServiceUnavailableException.class, () -> client.reserve(BOOKING_ID, 8L, 2L));
    }

    @Test
    void rejectsAMismatchedReservationResponse() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(reservationResponse(UUID.randomUUID(), 8L, 2L, "RESERVED")));

        assertThrows(InventoryServiceUnavailableException.class, () -> client.reserve(BOOKING_ID, 8L, 2L));
    }

    @Test
    void rejectsAReservationResponseWithoutUnitPrice() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 8L, 2L, "RESERVED", "null", "25.00"));
    }

    @Test
    void rejectsAReservationResponseWithoutTotalPrice() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 8L, 2L, "RESERVED", "12.50", "null"));
    }

    @Test
    void rejectsAReservationResponseWithNegativeUnitPrice() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 8L, 2L, "RESERVED", "-0.01", "25.00"));
    }

    @Test
    void rejectsAReservationResponseWithNegativeTotalPrice() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 8L, 2L, "RESERVED", "12.50", "-0.01"));
    }

    @Test
    void rejectsAReservationResponseWithMismatchedEventId() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 9L, 2L, "RESERVED"));
    }

    @Test
    void rejectsAReservationResponseWithMismatchedTicketCount() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 8L, 3L, "RESERVED"));
    }

    @Test
    void rejectsAReservationResponseWithNonReservedStatus() {
        assertInvalidReservationResponse(reservationResponse(BOOKING_ID, 8L, 2L, "RELEASED"));
    }

    private void assertInvalidReservationResponse(final String body) {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        assertThrows(InventoryServiceUnavailableException.class, () -> client.reserve(BOOKING_ID, 8L, 2L));
    }

    private static String reservationResponse(final UUID bookingId, final Long eventId, final Long ticketCount, final String status) {
        return reservationResponse(bookingId, eventId, ticketCount, status, "12.50", "25.00");
    }

    private static String reservationResponse(
            final UUID bookingId,
            final Long eventId,
            final Long ticketCount,
            final String status,
            final String unitPrice,
            final String totalPrice) {
        return """
                {"bookingId":"%s","eventId":%d,"ticketCount":%d,"status":"%s","unitPrice":%s,"totalPrice":%s}
                """.formatted(bookingId, eventId, ticketCount, status, unitPrice, totalPrice);
    }
}
