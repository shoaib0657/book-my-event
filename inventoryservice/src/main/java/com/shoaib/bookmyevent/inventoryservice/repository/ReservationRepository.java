package com.shoaib.bookmyevent.inventoryservice.repository;

import com.shoaib.bookmyevent.inventoryservice.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reservation persistence operations, including MySQL-specific arbitration for concurrent booking requests.
 *
 * <p>{@link #insertIfAbsent(String, Long, Long, java.math.BigDecimal, java.math.BigDecimal)} and
 * {@link #reservationWasCreated()} must execute in one transaction so both operations use the same JDBC session.</p>
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByBookingId(UUID bookingId);

    /**
     * Loads a reservation with a write lock before checking or changing its status.
     *
     * @param bookingId booking-owned reservation identifier
     * @return the locked reservation, or an empty result when it does not exist
     */
    // Protect status checks and release updates from racing with another reservation request.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation where reservation.bookingId = :bookingId")
    Optional<Reservation> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    /**
     * Attempts to insert a reservation without surfacing a duplicate-key error to concurrent callers.
     *
     * <p>The unique booking-ID constraint selects one winner. The statement records whether it inserted a row in a
     * connection-local flag, which the caller reads through {@link #reservationWasCreated()}.</p>
     *
     * @param bookingId booking UUID represented as the database CHAR value
     * @param eventId event being reserved
     * @param ticketCount number of tickets requested
     * @param unitPrice event price captured when the reservation is made
     * @param totalPrice calculated reservation total
     * @return the JDBC update count; use {@link #reservationWasCreated()} to determine insert versus replay
     */
    // MySQL-specific: the unique booking_id key decides which concurrent request wins.
    // A connection-local flag distinguishes a new insert from an idempotent replay.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO inventory_reservation
                (booking_id, event_id, ticket_count, unit_price, total_price, status)
            VALUES (
                :bookingId,
                :eventId,
                IF((@inventory_reservation_created := 1) IS NOT NULL, :ticketCount, :ticketCount),
                :unitPrice,
                :totalPrice,
                'RESERVED')
            ON DUPLICATE KEY UPDATE
                id = IF((@inventory_reservation_created := 0) IS NOT NULL, id, id)
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("bookingId") String bookingId,
            @Param("eventId") Long eventId,
            @Param("ticketCount") Long ticketCount,
            @Param("unitPrice") java.math.BigDecimal unitPrice,
            @Param("totalPrice") java.math.BigDecimal totalPrice);

    /**
     * Reports the outcome of the preceding {@link #insertIfAbsent} call on the same JDBC session.
     *
     * @return {@code 1} when a row was inserted or {@code 0} when the unique key already existed
     */
    // Must share the insert transaction so this query reads the same JDBC session variable.
    @Query(value = "SELECT @inventory_reservation_created", nativeQuery = true)
    Integer reservationWasCreated();
}
