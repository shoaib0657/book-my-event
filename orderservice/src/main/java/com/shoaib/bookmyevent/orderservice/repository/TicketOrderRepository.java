package com.shoaib.bookmyevent.orderservice.repository;

import com.shoaib.bookmyevent.orderservice.entity.TicketOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    Optional<TicketOrder> findByBookingId(UUID bookingId);

    /**
     * Inserts the Order-owned projection once and lets MySQL serialize concurrent attempts through the unique key.
     * The duplicate-key branch deliberately changes no business data.
     */
    @Modifying
    @Query(value = """
            INSERT INTO ticket_order (booking_id, customer_id, event_id, ticket_count, total_price)
            VALUES (:bookingId, :customerId, :eventId, :ticketCount, :totalPrice) AS incoming
            ON DUPLICATE KEY UPDATE booking_id = incoming.booking_id
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("bookingId") String bookingId,
            @Param("customerId") Long customerId,
            @Param("eventId") Long eventId,
            @Param("ticketCount") Long ticketCount,
            @Param("totalPrice") BigDecimal totalPrice);
}
