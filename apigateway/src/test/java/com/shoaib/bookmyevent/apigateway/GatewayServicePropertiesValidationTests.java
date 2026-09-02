package com.shoaib.bookmyevent.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayServicePropertiesValidationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withUserConfiguration(ApigatewayApplication.class);

	@Test
	void missingInventoryBaseUrlFailsStartupBinding() {
		contextRunner
				.withPropertyValues("gateway.services.booking-base-url=http://localhost:8081")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void malformedBookingBaseUrlFailsStartupBinding() {
		contextRunner
				.withPropertyValues(
						"gateway.services.inventory-base-url=http://localhost:8080",
						"gateway.services.booking-base-url=http://[")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void relativeInventoryBaseUrlFailsStartupValidation() {
		contextRunner
				.withPropertyValues(
						"gateway.services.inventory-base-url=/inventory",
						"gateway.services.booking-base-url=http://localhost:8081")
				.run(context -> assertThat(context).hasFailed());
	}
}
