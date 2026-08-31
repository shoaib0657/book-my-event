package com.shoaib.bookmyevent.bookingservice.messaging;

import com.shoaib.bookmyevent.bookingservice.event.BookingCreatedEvent;
import com.shoaib.bookmyevent.bookingservice.exception.BookingEventPublicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes versioned booking-created events and waits until Kafka acknowledges them.
 */
@Component
public class BookingEventPublisher {

    private final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public BookingEventPublisher(
            final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate,
            @Value("${booking.kafka.topic}") final String topic,
            @Value("${booking.kafka.publish-timeout}") final Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    /**
     * Publishes one event using the booking UUID as the Kafka key so related messages stay ordered.
     *
     * @param event confirmed booking to publish
     * @throws BookingEventPublicationException when Kafka rejects or does not acknowledge the event in time
     */
    public void publish(final BookingCreatedEvent event) {
        try {
            // The API only reports RESERVED after Kafka has acknowledged the message.
            kafkaTemplate.send(topic, event.bookingId().toString(), event)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publicationFailure(exception);
        } catch (final ExecutionException exception) {
            final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw publicationFailure(cause);
        } catch (final TimeoutException | RuntimeException exception) {
            throw publicationFailure(exception);
        }
    }

    private static BookingEventPublicationException publicationFailure(final Throwable cause) {
        return new BookingEventPublicationException("Booking event could not be published", cause);
    }
}
