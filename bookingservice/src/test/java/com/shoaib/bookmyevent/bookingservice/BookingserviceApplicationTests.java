package com.shoaib.bookmyevent.bookingservice;

import com.shoaib.bookmyevent.bookingservice.entity.Customer;
import com.shoaib.bookmyevent.bookingservice.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class BookingserviceApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
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
