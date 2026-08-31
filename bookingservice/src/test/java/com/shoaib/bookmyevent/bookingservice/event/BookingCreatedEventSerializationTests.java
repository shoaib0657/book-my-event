package com.shoaib.bookmyevent.bookingservice.event;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookingCreatedEventSerializationTests {

    @Test
    void serializesTheStableV1PayloadWithoutJavaTypeHeaders() {
        final BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.fromString("13c2a07c-2c04-416e-9e3f-a4c9e1a81b9e"),
                41L,
                8L,
                2L,
                new BigDecimal("25.00"));
        final RecordHeaders headers = new RecordHeaders();

        try (JacksonJsonSerializer<BookingCreatedEvent> serializer = new JacksonJsonSerializer<>()) {
            serializer.configure(Map.of(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false), false);
            final byte[] payload = serializer.serialize("booking-created-v1", headers, event);

            assertEquals(
                    "{\"bookingId\":\"13c2a07c-2c04-416e-9e3f-a4c9e1a81b9e\",\"customerId\":41,"
                            + "\"eventId\":8,\"ticketCount\":2,\"totalPrice\":25.00}",
                    new String(payload, StandardCharsets.UTF_8));
            assertNull(headers.lastHeader("__TypeId__"));
        }
    }
}
