package com.shoaib.bookmyevent.apigateway.route;

import java.net.URI;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;

/**
 * Produces the public error contract when Gateway cannot reach Booking Service.
 */
@Configuration(proxyBeanMethods = false)
class BookingFallbackRoutesConfiguration {

	private static final String FALLBACK_PATH = "/internal/fallback/booking-service";

	@Bean
	RouterFunction<ServerResponse> bookingServiceFallbackRoute() {
		return route("booking-service-fallback")
				.POST(FALLBACK_PATH, request -> {
					// A fallback is valid only when the circuit-breaker filter forwarded this request.
					if (request.attribute(MvcUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR).isEmpty()) {
						return ServerResponse.notFound().build();
					}

					ProblemDetail problem = ProblemDetail.forStatusAndDetail(
							HttpStatus.SERVICE_UNAVAILABLE,
							"Booking Service is temporarily unavailable. Please try again later.");
					problem.setTitle("Booking service unavailable");
					problem.setInstance(URI.create("/api/v1/bookings"));

					return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
							.contentType(MediaType.APPLICATION_PROBLEM_JSON)
							.body(problem);
				})
				.build();
	}
}
