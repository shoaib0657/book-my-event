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
class ApiDocsRoutesConfiguration {

	@Bean
	RouterFunction<ServerResponse> inventoryPublicDocsRoute(GatewayServiceProperties services) {
		return route("inventory-public-docs")
				.GET("/docs/inventory/v3/api-docs/public", http())
				.before(uri(services.inventoryBaseUrl()))
				.before(setPath("/v3/api-docs/public"))
				.build();
	}

	@Bean
	RouterFunction<ServerResponse> bookingPublicDocsRoute(GatewayServiceProperties services) {
		return route("booking-public-docs")
				.GET("/docs/booking/v3/api-docs/public", http())
				.before(uri(services.bookingBaseUrl()))
				.before(setPath("/v3/api-docs/public"))
				.build();
	}
}
