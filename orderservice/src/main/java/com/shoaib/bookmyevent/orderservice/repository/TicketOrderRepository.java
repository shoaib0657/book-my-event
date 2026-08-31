package com.shoaib.bookmyevent.orderservice.repository;

import com.shoaib.bookmyevent.orderservice.entity.TicketOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    Optional<TicketOrder> findByBookingId(UUID bookingId);
}
