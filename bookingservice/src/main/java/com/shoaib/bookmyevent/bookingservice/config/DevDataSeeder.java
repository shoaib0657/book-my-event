package com.shoaib.bookmyevent.bookingservice.config;

import com.shoaib.bookmyevent.bookingservice.entity.Customer;
import com.shoaib.bookmyevent.bookingservice.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevDataSeeder.class);

    private final CustomerRepository customerRepository;

    public DevDataSeeder(final CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        // Never overwrite or supplement intentional development data.
        if (customerRepository.count() > 0) {
            LOGGER.info("Skipping development customer seed because customer data already exists");
            return;
        }

        customerRepository.save(new Customer(
                null,
                "Demo Customer",
                "demo@bookmyevent.test",
                "Demo Address",
                null,
                null));
        LOGGER.info("Seeded 1 demo customer for the dev profile");
    }
}
