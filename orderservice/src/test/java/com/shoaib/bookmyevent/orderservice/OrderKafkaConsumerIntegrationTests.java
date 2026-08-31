package com.shoaib.bookmyevent.orderservice;

import com.shoaib.bookmyevent.orderservice.entity.TicketOrder;
import com.shoaib.bookmyevent.orderservice.repository.TicketOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = "booking-created-v1")
@SpringBootTest(properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.group-id=order-service-integration-test",
        "order.kafka.booking-topic=booking-created-v1"
})
class OrderKafkaConsumerIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Test
    void consumesRepeatedHeaderFreeJsonAndPersistsExactlyOneOrder() throws Exception {
        final UUID bookingId = UUID.fromString("05c94955-5658-44da-94ea-b026baa62ed4");
        final UUID markerBookingId = UUID.fromString("51e6ee38-1021-4f6f-a5ce-e2479483f4a3");
        final long ordersBefore = ticketOrderRepository.count();
        final String eventJson = """
                {
                  "bookingId": "05c94955-5658-44da-94ea-b026baa62ed4",
                  "customerId": 41,
                  "eventId": 8,
                  "ticketCount": 2,
                  "totalPrice": 100.00
                }
                """;
        final String markerJson = """
                {
                  "bookingId": "51e6ee38-1021-4f6f-a5ce-e2479483f4a3",
                  "customerId": 42,
                  "eventId": 9,
                  "ticketCount": 1,
                  "totalPrice": 50.00
                }
                """;

        kafkaTemplate.send("booking-created-v1", 0, bookingId.toString(), eventJson)
                .get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("booking-created-v1", 0, bookingId.toString(), eventJson)
                .get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("booking-created-v1", 0, markerBookingId.toString(), markerJson)
                .get(5, TimeUnit.SECONDS);

        // The marker is on the same partition, so reaching it proves both duplicate records were processed first.
        awaitOrder(markerBookingId, Duration.ofSeconds(10));
        final TicketOrder saved = ticketOrderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals(41L, saved.getCustomerId());
        assertEquals(8L, saved.getEventId());
        assertEquals(2L, saved.getTicketCount());
        assertEquals(new BigDecimal("100.00"), saved.getTotalPrice());
        assertEquals(ordersBefore + 2, ticketOrderRepository.count());
    }

    private TicketOrder awaitOrder(final UUID bookingId, final Duration timeout) throws InterruptedException {
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            final TicketOrder order = ticketOrderRepository.findByBookingId(bookingId).orElse(null);
            if (order != null) {
                return order;
            }
            Thread.sleep(100);
        }
        return fail("Order was not persisted before the timeout");
    }
}
