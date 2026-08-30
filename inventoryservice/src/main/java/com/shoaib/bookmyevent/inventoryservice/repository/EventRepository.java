package com.shoaib.bookmyevent.inventoryservice.repository;

import com.shoaib.bookmyevent.inventoryservice.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
}
