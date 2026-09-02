package com.shoaib.bookmyevent.apigateway.config;

import java.net.URI;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.services")
public record GatewayServiceProperties(
		@NotNull URI inventoryBaseUrl,
		@NotNull URI bookingBaseUrl) {

	@AssertTrue(message = "inventory-base-url must be an absolute HTTP(S) URI with a host")
	public boolean isInventoryBaseUrlValid() {
		return isHttpServiceUri(inventoryBaseUrl);
	}

	@AssertTrue(message = "booking-base-url must be an absolute HTTP(S) URI with a host")
	public boolean isBookingBaseUrlValid() {
		return isHttpServiceUri(bookingBaseUrl);
	}

	private static boolean isHttpServiceUri(URI value) {
		if (value == null) {
			return true;
		}
		String scheme = value.getScheme();
		return value.isAbsolute()
				&& value.getHost() != null
				&& ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
	}
}
