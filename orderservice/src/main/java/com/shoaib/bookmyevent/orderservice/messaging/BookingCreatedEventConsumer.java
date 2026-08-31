package com.shoaib.bookmyevent.orderservice.messaging;

import com.shoaib.bookmyevent.orderservice.event.BookingCreatedEvent;
import com.shoaib.bookmyevent.orderservice.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingCreatedEventConsumer {

    private final OrderService orderService;

    public BookingCreatedEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Converts an externally delivered booking event into Order Service's local state.
     * Persistence is delegated so Kafka transport and business behavior stay separate.
     */
    @KafkaListener(topics = "${order.kafka.booking-topic}")
    public void consume(BookingCreatedEvent event) {
        log.info("Received booking-created event for booking {}", event.bookingId());
        orderService.createFrom(event);
    }
}
