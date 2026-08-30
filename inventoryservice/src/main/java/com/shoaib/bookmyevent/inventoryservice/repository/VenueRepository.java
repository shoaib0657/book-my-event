package com.shoaib.bookmyevent.inventoryservice.repository;

import com.shoaib.bookmyevent.inventoryservice.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {
}
