package com.shoaib.bookmyevent.apigateway.route;

import java.net.URI;

import com.shoaib.bookmyevent.apigateway.config.GatewayServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration(proxyBeanMethods = false)
class BookingRoutesConfiguration {

	private static final String BOOKING_GATEWAY_CIRCUIT = "bookingServiceGateway";
	private static final URI BOOKING_FALLBACK = URI.create("forward:/internal/fallback/booking-service");

	@Bean
	RouterFunction<ServerResponse> bookingCreateRoute(GatewayServiceProperties services) {
		return route("booking-create")
				.POST("/api/v1/bookings", http())
				.before(uri(services.bookingBaseUrl()))
				.before(setPath("/api/v1/bookings"))
				.filter(circuitBreaker(BOOKING_GATEWAY_CIRCUIT, BOOKING_FALLBACK))
				.build();
	}
}
