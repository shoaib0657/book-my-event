package com.shoaib.bookmyevent.bookingservice.repository;

import com.shoaib.bookmyevent.bookingservice.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
