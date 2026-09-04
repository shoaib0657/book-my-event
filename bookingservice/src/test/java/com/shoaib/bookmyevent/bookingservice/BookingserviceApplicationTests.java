package com.shoaib.bookmyevent.bookingservice;

import com.shoaib.bookmyevent.bookingservice.entity.Customer;
import com.shoaib.bookmyevent.bookingservice.exception.BookingConflictException;
import com.shoaib.bookmyevent.bookingservice.exception.InventoryServiceUnavailableException;
import com.shoaib.bookmyevent.bookingservice.exception.ResourceNotFoundException;
import com.shoaib.bookmyevent.bookingservice.repository.CustomerRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BookingserviceApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Test
	void exposesSeparateInternalAndGatewayPublicOpenApiDocuments() throws Exception {
		mockMvc.perform(get("/v3/api-docs/internal"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paths", aMapWithSize(1)))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$['paths']['/api/v1/bookings']").exists())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.security").doesNotExist())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components.securitySchemes.bearerAuth").doesNotExist());

		mockMvc.perform(get("/v3/api-docs/public"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paths", aMapWithSize(1)))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$['paths']['/api/v1/bookings']").exists())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.servers[0].url").value("/"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.security[0].bearerAuth").isArray())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components.schemas.BookingRequest").exists())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
						"$.components.schemas.BookingRequest.properties.ticketCount.maximum").value(100));
	}

	@Test
	void contextLoads() {
	}

	@Test
	void configuresTheInventoryCircuitForInfrastructureFailuresOnly() {
		final CircuitBreakerConfig config =
				circuitBreakerRegistry.circuitBreaker("inventoryService").getCircuitBreakerConfig();

		assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, config.getSlidingWindowType());
		assertEquals(10, config.getSlidingWindowSize());
		assertEquals(5, config.getMinimumNumberOfCalls());
		assertEquals(50.0F, config.getFailureRateThreshold());
		assertEquals(3, config.getPermittedNumberOfCallsInHalfOpenState());
		assertEquals(20_000L, config.getWaitIntervalFunctionInOpenState().apply(1));
		assertTrue(config.isAutomaticTransitionFromOpenToHalfOpenEnabled());
		assertTrue(config.getRecordExceptionPredicate().test(
				new InventoryServiceUnavailableException("Inventory failed")));
		assertFalse(config.getIgnoreExceptionPredicate().test(
				new InventoryServiceUnavailableException("Inventory failed")));
		assertTrue(config.getIgnoreExceptionPredicate().test(
				new ResourceNotFoundException("Event not found")));
		assertTrue(config.getIgnoreExceptionPredicate().test(
				new BookingConflictException("Capacity unavailable")));
	}

	@Test
	void exposesHealthMetricsAndCircuitStateWithoutExposingEveryActuatorEndpoint() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/circuitbreakers"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/env"))
				.andExpect(status().isNotFound());
	}

	@Test
	void savesCustomerAndGeneratesAnId() {
		final Customer customer = customerRepository.saveAndFlush(
				new Customer(null, "Test Customer", "customer-one@bookmyevent.test", "Test Address", null, null));

		assertNotNull(customer.getId());
		assertEquals("customer-one@bookmyevent.test", customerRepository.findById(customer.getId()).orElseThrow().getEmail());
	}

	@Test
	void rejectsDuplicateCustomerEmail() {
		customerRepository.saveAndFlush(
				new Customer(null, "First Customer", "duplicate@bookmyevent.test", "First Address", null, null));

		assertThrows(DataIntegrityViolationException.class, () -> customerRepository.saveAndFlush(
				new Customer(null, "Second Customer", "duplicate@bookmyevent.test", "Second Address", null, null)));
	}

	@Test
	void databaseRejectsBlankCustomerNameAndAddress() {
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
				INSERT INTO customer (name, email, address)
				VALUES ('   ', 'blank-name@bookmyevent.test', 'Test Address')
				"""));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
				INSERT INTO customer (name, email, address)
				VALUES ('Test Customer', 'blank-address@bookmyevent.test', '   ')
				"""));
	}

}
