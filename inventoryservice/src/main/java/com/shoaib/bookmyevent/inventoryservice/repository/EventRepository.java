package com.shoaib.bookmyevent.inventoryservice.repository;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Fetch the venue explicitly for API reads because the relationship is lazy
    // and Open Session in View is disabled.
    @EntityGraph(attributePaths = "venue")
    List<Event> findAllByOrderByIdAsc();

    @Override
    @EntityGraph(attributePaths = "venue")
    Optional<Event> findById(Long id);

    // Keep the availability check and decrement in one database statement so
    // concurrent requests cannot reserve the same remaining capacity.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Event event
            set event.leftCapacity = event.leftCapacity - :ticketsBooked
            where event.id = :eventId
              and event.leftCapacity >= :ticketsBooked
            """)
    int decrementCapacityIfAvailable(
            @Param("eventId") Long eventId,
            @Param("ticketsBooked") Long ticketsBooked);
}
