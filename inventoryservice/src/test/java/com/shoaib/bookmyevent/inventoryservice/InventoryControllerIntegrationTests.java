package com.shoaib.bookmyevent.inventoryservice;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import com.shoaib.bookmyevent.inventoryservice.entity.Venue;
import com.shoaib.bookmyevent.inventoryservice.repository.EventRepository;
import com.shoaib.bookmyevent.inventoryservice.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "logging.level.root=WARN")
@AutoConfigureMockMvc
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class InventoryControllerIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void exposesSeparateInternalAndGatewayPublicOpenApiDocuments() throws Exception {
        mockMvc.perform(get("/v3/api-docs/internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", aMapWithSize(5)))
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/events']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/venue/{venueId}']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/event/{eventId}']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/reservations']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/reservations/{bookingId}/release']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/venue/{venueId}'].get.parameters[0].schema.type")
                        .value("integer"))
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/venue/{venueId}'].get.parameters[0].schema.format")
                        .value("int64"))
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/venue/{venueId}'].get.parameters[0].schema.exclusiveMinimum")
                        .value(0))
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/event/{eventId}'].get.parameters[0].schema.type")
                        .value("integer"))
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/event/{eventId}'].get.parameters[0].schema.format")
                        .value("int64"))
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/event/{eventId}'].get.parameters[0].schema.exclusiveMinimum")
                        .value(0));

        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", aMapWithSize(2)))
                .andExpect(jsonPath("$['paths']['/api/v1/events']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/events/{eventId}']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/venue/{venueId}']").doesNotExist())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/reservations']").doesNotExist())
                .andExpect(jsonPath("$['paths']['/api/v1/inventory/reservations/{bookingId}/release']").doesNotExist())
                .andExpect(jsonPath("$['paths']['/api/v1/events/{eventId}'].get.parameters[0].schema.type")
                        .value("integer"))
                .andExpect(jsonPath("$['paths']['/api/v1/events/{eventId}'].get.parameters[0].schema.format")
                        .value("int64"))
                .andExpect(jsonPath("$['paths']['/api/v1/events/{eventId}'].get.parameters[0].schema.exclusiveMinimum")
                        .value(0))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void returnsNotFoundWhenVenueDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/venue/{venueId}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listsEventsUsingTheStableInventoryContract() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(3))
                .andExpect(jsonPath("$[0].eventName").value("Coldplay"))
                .andExpect(jsonPath("$[0].totalCapacity").value(40000))
                .andExpect(jsonPath("$[0].remainingCapacity").value(40000))
                .andExpect(jsonPath("$[0].ticketPrice").value(10.00))
                .andExpect(jsonPath("$[0].venue.venueId").value(1))
                .andExpect(jsonPath("$[0].venue.venueName").value("Old Trafford"))
                .andExpect(jsonPath("$[0].venue.address").value("Manchester, UK"))
                .andExpect(jsonPath("$[0].venue.totalCapacity").value(80000))
                .andExpect(jsonPath("$[1].eventId").value(4));
    }

    @Test
    void returnsEventInventoryById() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(3))
                .andExpect(jsonPath("$.eventName").value("Coldplay"))
                .andExpect(jsonPath("$.remainingCapacity").value(40000))
                .andExpect(jsonPath("$.ticketPrice").value(10.00))
                .andExpect(jsonPath("$.venue.venueId").value(1));
    }

    @Test
    void returnsNotFoundWhenEventDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void preventsConcurrentReservationsFromOversellingAnEvent() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final Callable<Integer> reserveTickets = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return mockMvc.perform(post("/api/v1/inventory/reservations")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"bookingId":"%s","eventId":3,"ticketCount":30000}
                                    """.formatted(java.util.UUID.randomUUID())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try {
            final Future<Integer> firstBooking = executor.submit(reserveTickets);
            final Future<Integer> secondBooking = executor.submit(reserveTickets);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            final List<Integer> statuses = List.of(
                            firstBooking.get(10, TimeUnit.SECONDS),
                            secondBooking.get(10, TimeUnit.SECONDS))
                    .stream()
                    .sorted()
                    .toList();
            assertEquals(List.of(201, 409), statuses);

            mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remainingCapacity").value(10_000));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidReservationRequests() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"11111111-1111-1111-1111-111111111111","eventId":3,"ticketCount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsVenueInventoryById() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/venue/{venueId}", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").value(2))
                .andExpect(jsonPath("$.venueName").value("Etihad Stadium"))
                .andExpect(jsonPath("$.address").value("Manchester, UK"))
                .andExpect(jsonPath("$.totalCapacity").value(70_000));
    }

    @Test
    void createsReservationAndDecrementsCapacity() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"11111111-1111-1111-1111-111111111111","eventId":3,"ticketCount":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.eventId").value(3))
                .andExpect(jsonPath("$.ticketCount").value(2))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.unitPrice").value(10.00))
                .andExpect(jsonPath("$.totalPrice").value(20.00));

        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCapacity").value(39_998));
    }

    @Test
    void returnsExistingReservationWithoutDecrementingCapacityAgain() throws Exception {
        final String body = """
                {"bookingId":"22222222-2222-2222-2222-222222222222","eventId":3,"ticketCount":2}
                """;
        mockMvc.perform(post("/api/v1/inventory/reservations").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/inventory/reservations").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCapacity").value(39_998));
    }

    @Test
    void rejectsConflictingReservationReplay() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"33333333-3333-3333-3333-333333333333","eventId":3,"ticketCount":2}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"33333333-3333-3333-3333-333333333333","eventId":4,"ticketCount":2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reservation conflict"))
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 4))
                .andExpect(jsonPath("$.remainingCapacity").value(30_000));
    }

    @Test
    void returnsConflictWithoutReservationOrCapacityChangeWhenTicketsAreUnavailable() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"44444444-4444-4444-4444-444444444444","eventId":3,"ticketCount":40001}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient capacity"))
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCapacity").value(40_000));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_reservation WHERE booking_id = ?", Integer.class,
                "44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void returnsNotFoundWhenReservingAMissingEvent() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"55555555-5555-5555-5555-555555555555","eventId":999999,"ticketCount":2}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsNonPositiveResourceIds() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/venue/{venueId}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void databaseEnforcesInventoryCapacityInvariants() {
        assertAll(
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO venue (id, name, address, total_capacity)
                        VALUES (99, 'Invalid Venue', 'Nowhere', 0)
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO event (id, name, total_capacity, left_capacity, venue_id, ticket_price)
                        VALUES (100, 'Zero Capacity', 0, 0, 1, 10.00)
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO event (id, name, total_capacity, left_capacity, venue_id, ticket_price)
                        VALUES (101, 'Negative Remaining', 10, -1, 1, 10.00)
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO event (id, name, total_capacity, left_capacity, venue_id, ticket_price)
                        VALUES (102, 'Too Much Remaining', 10, 11, 1, 10.00)
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO event (id, name, total_capacity, left_capacity, venue_id, ticket_price)
                        VALUES (103, 'Negative Price', 10, 10, 1, -1.00)
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO inventory_reservation
                            (booking_id, event_id, ticket_count, unit_price, total_price, status)
                        VALUES ('66666666-6666-6666-6666-666666666666', 3, 0, 10.00, 0.00, 'RESERVED')
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO inventory_reservation
                            (booking_id, event_id, ticket_count, unit_price, total_price, status)
                        VALUES ('77777777-7777-7777-7777-777777777777', 3, 1, -1.00, -1.00, 'RESERVED')
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO inventory_reservation
                            (booking_id, event_id, ticket_count, unit_price, total_price, status)
                        VALUES ('88888888-8888-8888-8888-888888888888', 3, 1, 10.00, 10.00, 'INVALID')
                        """)),
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO inventory_reservation
                            (booking_id, event_id, ticket_count, unit_price, total_price, status)
                        VALUES ('abababab-abab-abab-abab-abababababab', 3, 1, 0.00, -0.01, 'RESERVED')
                        """)));
    }

    @Test
    void databaseEnforcesUniqueBookingIdsAndNonCascadingReservationEventReferences() {
        jdbcTemplate.update("""
                INSERT INTO inventory_reservation
                    (booking_id, event_id, ticket_count, unit_price, total_price, status)
                VALUES ('cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd', 3, 1, 10.00, 10.00, 'RESERVED')
                """);

        assertAll(
                () -> assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                        INSERT INTO inventory_reservation
                            (booking_id, event_id, ticket_count, unit_price, total_price, status)
                        VALUES ('cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd', 4, 1, 10.00, 10.00, 'RESERVED')
                        """)),
                () -> assertThrows(DataAccessException.class,
                        () -> jdbcTemplate.update("DELETE FROM event WHERE id = 3")));
    }

    @Test
    void noLongerExposesTheLegacyCapacityMutationEndpoint() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/event/{eventId}/capacity/{ticketsBooked}", 3, 2))
                .andExpect(result -> assertTrue(List.of(404, 405)
                        .contains(result.getResponse().getStatus())));
    }

    @Test
    void concurrentIdenticalReservationsDecrementCapacityOnlyOnce() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final Callable<Integer> reserve = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return mockMvc.perform(post("/api/v1/inventory/reservations")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"bookingId":"99999999-9999-9999-9999-999999999999","eventId":3,"ticketCount":2}
                                    """))
                    .andReturn().getResponse().getStatus();
        };
        try {
            final Future<Integer> first = executor.submit(reserve);
            final Future<Integer> second = executor.submit(reserve);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(List.of(200, 201), List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
            mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                    .andExpect(jsonPath("$.remainingCapacity").value(39_998));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentConflictingReservationsReturnConflictWithoutDecrementingBothEvents() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final String bookingId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        final Callable<Integer> reserveFirstEvent = () -> reserveConcurrently(
                ready, start, bookingId, 3, 2);
        final Callable<Integer> reserveSecondEvent = () -> reserveConcurrently(
                ready, start, bookingId, 4, 3);

        try {
            final Future<Integer> first = executor.submit(reserveFirstEvent);
            final Future<Integer> second = executor.submit(reserveSecondEvent);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(List.of(201, 409), List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory_reservation WHERE booking_id = ?", Integer.class, bookingId));

            final long eventThreeCapacity = jdbcTemplate.queryForObject(
                    "SELECT left_capacity FROM event WHERE id = 3", Long.class);
            final long eventFourCapacity = jdbcTemplate.queryForObject(
                    "SELECT left_capacity FROM event WHERE id = 4", Long.class);
            assertTrue(
                    (eventThreeCapacity == 39_998 && eventFourCapacity == 30_000)
                            || (eventThreeCapacity == 40_000 && eventFourCapacity == 29_997));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releasesReservationOnceAndRejectsFurtherReservationReplay() throws Exception {
        final String bookingId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        final String body = """
                {"bookingId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","eventId":3,"ticketCount":2}
                """;
        mockMvc.perform(post("/api/v1/inventory/reservations").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/inventory/reservations/{bookingId}/release", bookingId))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/inventory/reservations/{bookingId}/release", bookingId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/inventory/reservations").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reservation conflict"));
        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(jsonPath("$.remainingCapacity").value(40_000));
    }

    @Test
    void returnsNotFoundWhenReleasingUnknownReservation() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/reservations/{bookingId}/release",
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private int reserveConcurrently(
            final CountDownLatch ready,
            final CountDownLatch start,
            final String bookingId,
            final long eventId,
            final long ticketCount) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingId":"%s","eventId":%d,"ticketCount":%d}
                                """.formatted(bookingId, eventId, ticketCount)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void generatesIdsWhenNewInventoryEntitiesAreSaved() {
        final Venue venue = venueRepository.saveAndFlush(
                new Venue(null, "Generated Venue", "Test City", 1_000L));
        final Event event = eventRepository.saveAndFlush(
                new Event(null, "Generated Event", 500L, 500L, venue, new BigDecimal("25.00")));

        assertAll(
                () -> assertNotNull(venue.getId()),
                () -> assertNotNull(event.getId()));
    }
}
