package com.shoaib.bookmyevent.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayServicePropertiesValidationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withUserConfiguration(ApigatewayApplication.class)
			.withBean(JwtDecoder.class, () -> token -> {
				throw new JwtException("Token decoding is outside this configuration-binding test");
			});

	@Test
	void missingInventoryBaseUrlFailsStartupBinding() {
		contextRunner
				.withPropertyValues("gateway.services.booking-base-url=http://localhost:8081")
				.run(context -> assertGatewayServiceBindingFailed(context));
	}

	@Test
	void malformedBookingBaseUrlFailsStartupBinding() {
		contextRunner
				.withPropertyValues(
						"gateway.services.inventory-base-url=http://localhost:8080",
						"gateway.services.booking-base-url=http://[")
				.run(context -> assertGatewayServiceBindingFailed(context));
	}

	@Test
	void relativeInventoryBaseUrlFailsStartupValidation() {
		contextRunner
				.withPropertyValues(
						"gateway.services.inventory-base-url=/inventory",
						"gateway.services.booking-base-url=http://localhost:8081")
				.run(context -> assertGatewayServiceBindingFailed(context));
	}

	private static void assertGatewayServiceBindingFailed(
			AssertableWebApplicationContext context) {
		assertThat(context).hasFailed();
		assertThat(context.getStartupFailure())
				.hasStackTraceContaining("Could not bind properties to 'GatewayServiceProperties'");
	}
}
