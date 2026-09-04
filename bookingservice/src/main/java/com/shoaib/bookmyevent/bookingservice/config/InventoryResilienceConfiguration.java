package com.shoaib.bookmyevent.bookingservice.config;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the circuit used to protect new Inventory reservation requests.
 */
@Configuration(proxyBeanMethods = false)
public class InventoryResilienceConfiguration {

    private static final String INVENTORY_CIRCUIT_BREAKER = "inventoryService";

    @Bean
    CircuitBreaker inventoryServiceCircuitBreaker(final CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        return circuitBreakerFactory.create(INVENTORY_CIRCUIT_BREAKER);
    }
}
