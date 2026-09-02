package com.shoaib.bookmyevent.apigateway.route;

import com.shoaib.bookmyevent.apigateway.config.GatewayServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration(proxyBeanMethods = false)
class InventoryRoutesConfiguration {

	@Bean
	RouterFunction<ServerResponse> inventoryEventsListRoute(GatewayServiceProperties services) {
		return route("inventory-events-list")
				.GET("/api/v1/events", http())
				.before(uri(services.inventoryBaseUrl()))
				.before(setPath("/api/v1/inventory/events"))
				.build();
	}

	@Bean
	RouterFunction<ServerResponse> inventoryEventDetailRoute(GatewayServiceProperties services) {
		return route("inventory-event-detail")
				.GET("/api/v1/events/{eventId}", http())
				.before(uri(services.inventoryBaseUrl()))
				.before(setPath("/api/v1/inventory/event/{eventId}"))
				.build();
	}
}
