package com.shoaib.bookmyevent.bookingservice;

import com.shoaib.bookmyevent.bookingservice.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(properties = "logging.level.root=WARN")
class DevDataSeederIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void seedsOneDemoCustomerWhenTheDevDatabaseIsEmpty() {
        assertEquals(1, customerRepository.count());
        assertEquals(
                "demo@bookmyevent.test",
                customerRepository.findAll().getFirst().getEmail());
    }
}
