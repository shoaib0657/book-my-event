package com.shoaib.bookmyevent.orderservice;

import com.shoaib.bookmyevent.orderservice.entity.TicketOrder;
import com.shoaib.bookmyevent.orderservice.repository.TicketOrderRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class OrderserviceApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Autowired
	private TicketOrderRepository ticketOrderRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void savesAndFindsAnOrderByBookingId() {
		final UUID bookingId = UUID.fromString("0f5fa5e6-4726-4ae9-ad65-d927f8b2016a");
		final TicketOrder order = ticketOrderRepository.saveAndFlush(
				new TicketOrder(null, bookingId, 1L, 2L, 3L, new BigDecimal("150.00"), null));

		assertNotNull(order.getId());
		assertEquals(bookingId, ticketOrderRepository.findByBookingId(bookingId).orElseThrow().getBookingId());
	}

	@Test
	void rejectsDuplicateBookingId() {
		final UUID bookingId = UUID.fromString("e508a013-c048-4bd1-ada4-f3f8243c6f0e");
		ticketOrderRepository.saveAndFlush(
				new TicketOrder(null, bookingId, 1L, 2L, 1L, new BigDecimal("50.00"), null));

		assertThrows(DataIntegrityViolationException.class, () -> ticketOrderRepository.saveAndFlush(
				new TicketOrder(null, bookingId, 1L, 2L, 1L, new BigDecimal("50.00"), null)));
	}

	@Test
	void databaseRejectsInvalidOrderValues() {
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
				INSERT INTO ticket_order (booking_id, customer_id, event_id, ticket_count, total_price)
				VALUES ('40070fe5-c4ee-4d7b-8ce8-56e7315ce51a', 1, 2, 0, 50.00)
				"""));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
				INSERT INTO ticket_order (booking_id, customer_id, event_id, ticket_count, total_price)
				VALUES ('8499f4bc-d61b-4c1d-8a61-13df512d7141', 1, 2, 1, -0.01)
				"""));
	}

}
