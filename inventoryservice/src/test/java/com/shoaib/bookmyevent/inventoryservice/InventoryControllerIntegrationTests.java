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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void preventsConcurrentBookingsFromOversellingAnEvent() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final Callable<Integer> bookTickets = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return mockMvc.perform(put(
                            "/api/v1/inventory/event/{eventId}/capacity/{ticketsBooked}",
                            3,
                            30_000))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try {
            final Future<Integer> firstBooking = executor.submit(bookTickets);
            final Future<Integer> secondBooking = executor.submit(bookTickets);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            final List<Integer> statuses = List.of(
                            firstBooking.get(10, TimeUnit.SECONDS),
                            secondBooking.get(10, TimeUnit.SECONDS))
                    .stream()
                    .sorted()
                    .toList();
            assertEquals(List.of(200, 409), statuses);

            mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remainingCapacity").value(10_000));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNonPositiveTicketCounts() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/inventory/event/{eventId}/capacity/{ticketsBooked}",
                        3,
                        0))
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
    void decrementsAvailableCapacity() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/inventory/event/{eventId}/capacity/{ticketsBooked}",
                        3,
                        2))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("", result.getResponse().getContentAsString()));

        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCapacity").value(39_998));
    }

    @Test
    void returnsConflictWithoutChangingCapacityWhenTicketsAreUnavailable() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/inventory/event/{eventId}/capacity/{ticketsBooked}",
                        3,
                        40_001))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient capacity"))
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/v1/inventory/event/{eventId}", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCapacity").value(40_000));
    }

    @Test
    void returnsNotFoundWhenUpdatingAMissingEvent() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/inventory/event/{eventId}/capacity/{ticketsBooked}",
                        999_999,
                        2))
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
                        """)));
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
