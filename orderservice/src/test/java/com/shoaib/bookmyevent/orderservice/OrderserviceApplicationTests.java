package com.shoaib.bookmyevent.orderservice;

import com.shoaib.bookmyevent.orderservice.entity.TicketOrder;
import com.shoaib.bookmyevent.orderservice.event.BookingCreatedEvent;
import com.shoaib.bookmyevent.orderservice.repository.TicketOrderRepository;
import com.shoaib.bookmyevent.orderservice.service.ConflictingBookingEventException;
import com.shoaib.bookmyevent.orderservice.service.OrderService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class OrderserviceApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Autowired
	private TicketOrderRepository ticketOrderRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OrderService orderService;

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
	void persistsEveryConfirmedFieldFromABookingCreatedEvent() {
		final UUID bookingId = UUID.fromString("7ab72fa2-b2e3-4123-8280-cef87b118959");

		orderService.createFrom(new BookingCreatedEvent(
				bookingId, 41L, 8L, 3L, new BigDecimal("150.00")));

		final TicketOrder saved = ticketOrderRepository.findByBookingId(bookingId).orElseThrow();
		assertEquals(bookingId, saved.getBookingId());
		assertEquals(41L, saved.getCustomerId());
		assertEquals(8L, saved.getEventId());
		assertEquals(3L, saved.getTicketCount());
		assertEquals(new BigDecimal("150.00"), saved.getTotalPrice());
		assertNotNull(saved.getCreatedAt());
	}

	@Test
	void ignoresARepeatedBookingEventInsteadOfCreatingAnotherOrder() {
		final UUID bookingId = UUID.fromString("c61d2d97-498f-4f70-ac2e-250b65306353");
		final BookingCreatedEvent event = new BookingCreatedEvent(
				bookingId, 41L, 8L, 2L, new BigDecimal("100.00"));
		final long ordersBefore = ticketOrderRepository.count();

		orderService.createFrom(event);
		orderService.createFrom(event);

		assertEquals(ordersBefore + 1, ticketOrderRepository.count());
	}

	@Test
	void concurrentDeliveriesOfTheSameEventCreateExactlyOneOrder() throws Exception {
		final UUID bookingId = UUID.fromString("1fef49c7-e863-4192-aebe-f3aaa49c156f");
		final BookingCreatedEvent event = new BookingCreatedEvent(
				bookingId, 41L, 8L, 2L, new BigDecimal("100.00"));
		final int deliveryCount = 12;
		final long ordersBefore = ticketOrderRepository.count();
		final CountDownLatch ready = new CountDownLatch(deliveryCount);
		final CountDownLatch start = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(deliveryCount);
		final List<Future<Void>> deliveries = new ArrayList<>();

		try {
			for (int i = 0; i < deliveryCount; i++) {
				deliveries.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Concurrent deliveries did not start in time");
					}
					orderService.createFrom(event);
					return null;
				}));
			}

			if (!ready.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent deliveries were not ready in time");
			}
			start.countDown();
			for (final Future<Void> delivery : deliveries) {
				delivery.get(20, TimeUnit.SECONDS);
			}
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertEquals(ordersBefore + 1, ticketOrderRepository.count());
	}

	@Test
	void rejectsARepeatedBookingIdWithConflictingOrderData() {
		final UUID bookingId = UUID.fromString("f8354b9d-871d-4128-8f9d-2962a856205a");
		final BookingCreatedEvent original = new BookingCreatedEvent(
				bookingId, 41L, 8L, 2L, new BigDecimal("100.00"));
		final BookingCreatedEvent conflicting = new BookingCreatedEvent(
				bookingId, 41L, 8L, 3L, new BigDecimal("150.00"));

		orderService.createFrom(original);

		assertThrows(ConflictingBookingEventException.class, () -> orderService.createFrom(conflicting));
		final TicketOrder saved = ticketOrderRepository.findByBookingId(bookingId).orElseThrow();
		assertEquals(2L, saved.getTicketCount());
		assertEquals(new BigDecimal("100.00"), saved.getTotalPrice());
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
