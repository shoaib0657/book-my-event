package com.shoaib.bookmyevent.inventoryservice.repository;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * Loads an event while holding a database write lock for the lifetime of the current transaction.
     *
     * <p>Callers must use this method inside an active transaction before checking or changing capacity.</p>
     *
     * @param eventId event to lock
     * @return the locked event, or an empty result when it does not exist
     */
    // Serialize capacity changes for one event; the lock is held until the service transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from Event event where event.id = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);
}
