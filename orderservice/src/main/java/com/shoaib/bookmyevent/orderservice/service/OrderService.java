package com.shoaib.bookmyevent.orderservice.service;

import com.shoaib.bookmyevent.orderservice.entity.TicketOrder;
import com.shoaib.bookmyevent.orderservice.event.BookingCreatedEvent;
import com.shoaib.bookmyevent.orderservice.repository.TicketOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Slf4j
public class OrderService {

    private final TicketOrderRepository ticketOrderRepository;

    public OrderService(final TicketOrderRepository ticketOrderRepository) {
        this.ticketOrderRepository = ticketOrderRepository;
    }

    /**
     * Materializes a confirmed booking as an Order-owned record.
     *
     * <p>Kafka may deliver the same record more than once. An atomic database insert makes an identical repeated
     * delivery a no-op even when multiple consumers reach the database concurrently. Reusing the booking UUID for
     * different order data is rejected instead of being silently ignored.</p>
     *
     * @param event confirmed booking received from Kafka
     */
    @Transactional
    public void createFrom(final BookingCreatedEvent event) {
        ticketOrderRepository.insertIfAbsent(
                event.bookingId().toString(),
                event.customerId(),
                event.eventId(),
                event.ticketCount(),
                event.totalPrice());

        final TicketOrder storedOrder = ticketOrderRepository.findByBookingId(event.bookingId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order was not found after inserting booking " + event.bookingId()));

        if (!matches(storedOrder, event)) {
            log.error("Rejecting conflicting booking event for booking {}", event.bookingId());
            throw new ConflictingBookingEventException(event.bookingId());
        }
    }

    private boolean matches(final TicketOrder storedOrder, final BookingCreatedEvent event) {
        return Objects.equals(storedOrder.getCustomerId(), event.customerId())
                && Objects.equals(storedOrder.getEventId(), event.eventId())
                && Objects.equals(storedOrder.getTicketCount(), event.ticketCount())
                && storedOrder.getTotalPrice().compareTo(event.totalPrice()) == 0;
    }
}
