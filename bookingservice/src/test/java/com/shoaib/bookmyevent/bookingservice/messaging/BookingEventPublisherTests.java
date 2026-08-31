package com.shoaib.bookmyevent.bookingservice.messaging;

import com.shoaib.bookmyevent.bookingservice.event.BookingCreatedEvent;
import com.shoaib.bookmyevent.bookingservice.exception.BookingEventPublicationException;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingEventPublisherTests {

    private static final String TOPIC = "booking-created-v1";
    private static final UUID BOOKING_ID = UUID.fromString("13c2a07c-2c04-416e-9e3f-a4c9e1a81b9e");
    private static final BookingCreatedEvent EVENT = new BookingCreatedEvent(
            BOOKING_ID, 41L, 8L, 2L, new BigDecimal("25.00"));

    @Test
    void publishesWithTheBookingIdAsKeyAndWaitsForTheAcknowledgement() {
        final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate = kafkaTemplate();
        final SendResult<String, BookingCreatedEvent> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(TOPIC, BOOKING_ID.toString(), EVENT))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        final BookingEventPublisher publisher = publisher(kafkaTemplate, Duration.ofSeconds(1));

        publisher.publish(EVENT);

        verify(kafkaTemplate).send(TOPIC, BOOKING_ID.toString(), EVENT);
    }

    @Test
    void turnsAnAsynchronousKafkaFailureIntoAPublicationFailure() {
        final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate = kafkaTemplate();
        final KafkaException kafkaFailure = new KafkaException("broker unavailable");
        when(kafkaTemplate.send(TOPIC, BOOKING_ID.toString(), EVENT))
                .thenReturn(CompletableFuture.failedFuture(kafkaFailure));
        final BookingEventPublisher publisher = publisher(kafkaTemplate, Duration.ofSeconds(1));

        final BookingEventPublicationException thrown = assertThrows(
                BookingEventPublicationException.class, () -> publisher.publish(EVENT));

        assertSame(kafkaFailure, thrown.getCause());
    }

    @Test
    void turnsASynchronousKafkaFailureIntoAPublicationFailure() {
        final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate = kafkaTemplate();
        final KafkaException kafkaFailure = new KafkaException("serialization failed");
        when(kafkaTemplate.send(TOPIC, BOOKING_ID.toString(), EVENT)).thenThrow(kafkaFailure);
        final BookingEventPublisher publisher = publisher(kafkaTemplate, Duration.ofSeconds(1));

        final BookingEventPublicationException thrown = assertThrows(
                BookingEventPublicationException.class, () -> publisher.publish(EVENT));

        assertSame(kafkaFailure, thrown.getCause());
    }

    @Test
    void failsWhenKafkaDoesNotAcknowledgeWithinTheConfiguredTimeout() {
        final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate = kafkaTemplate();
        when(kafkaTemplate.send(TOPIC, BOOKING_ID.toString(), EVENT)).thenReturn(new CompletableFuture<>());
        final BookingEventPublisher publisher = publisher(kafkaTemplate, Duration.ofMillis(1));

        assertThrows(BookingEventPublicationException.class, () -> publisher.publish(EVENT));
    }

    @Test
    void restoresTheInterruptFlagWhenWaitingForKafkaIsInterrupted() {
        final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate = kafkaTemplate();
        when(kafkaTemplate.send(TOPIC, BOOKING_ID.toString(), EVENT)).thenReturn(new CompletableFuture<>());
        final BookingEventPublisher publisher = publisher(kafkaTemplate, Duration.ofSeconds(1));

        Thread.currentThread().interrupt();
        try {
            assertThrows(BookingEventPublicationException.class, () -> publisher.publish(EVENT));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Do not leak the deliberate interrupt into JUnit's worker thread.
            Thread.interrupted();
        }
    }

    private static BookingEventPublisher publisher(
            final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate, final Duration timeout) {
        return new BookingEventPublisher(kafkaTemplate, TOPIC, timeout);
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
