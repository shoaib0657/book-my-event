package com.shoaib.bookmyevent.apigateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.StreamSupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesIntegrationTests {

	private static final WireMockServer inventory = new WireMockServer(wireMockConfig().dynamicPort());
	private static final WireMockServer booking = new WireMockServer(wireMockConfig().dynamicPort());

	static {
		inventory.start();
		booking.start();
	}

	@DynamicPropertySource
	static void downstreamServices(DynamicPropertyRegistry registry) {
		registry.add("gateway.services.inventory-base-url", inventory::baseUrl);
		registry.add("gateway.services.booking-base-url", booking::baseUrl);
	}

	@LocalServerPort
	private int gatewayPort;

	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void resetDownstreams() {
		inventory.resetAll();
		booking.resetAll();
	}

	@AfterAll
	static void stopDownstreams() {
		inventory.stop();
		booking.stop();
	}

	@Test
	void listEventsRewritesToInventoryAndPassesResponseMetadata() throws Exception {
		inventory.stubFor(get(urlEqualTo("/api/v1/inventory/events"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withHeader("X-Inventory-Version", "17")
						.withBody("[{\"id\":41,\"name\":\"Jazz Night\"}]")));

		HttpResponse<String> response = send(getRequest("/api/v1/events")
				.header("X-Correlation-Id", "list-123")
				.build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
		assertThat(response.headers().firstValue("X-Inventory-Version")).hasValue("17");
		assertThat(response.body()).isEqualTo("[{\"id\":41,\"name\":\"Jazz Night\"}]");
		inventory.verify(getRequestedFor(urlEqualTo("/api/v1/inventory/events"))
				.withHeader("X-Correlation-Id", equalTo("list-123")));
	}

	@Test
	void eventDetailRewritesPathPreservesQueryAndPassesDownstream404() throws Exception {
		inventory.stubFor(get(urlPathEqualTo("/api/v1/inventory/event/41"))
				.withQueryParam("locale", equalTo("en-IN"))
				.withQueryParam("include", equalTo("venue"))
				.willReturn(aResponse()
						.withStatus(404)
						.withHeader("Content-Type", "application/problem+json")
						.withHeader("X-Inventory-Error", "event-missing")
						.withBody("{\"title\":\"Event not found\"}")));

		HttpResponse<String> response = send(getRequest("/api/v1/events/41?locale=en-IN&include=venue").build());

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
		assertThat(response.headers().firstValue("X-Inventory-Error")).hasValue("event-missing");
		assertThat(response.body()).isEqualTo("{\"title\":\"Event not found\"}");
		inventory.verify(getRequestedFor(urlPathEqualTo("/api/v1/inventory/event/41"))
				.withQueryParam("locale", equalTo("en-IN"))
				.withQueryParam("include", equalTo("venue")));
	}

	@Test
	void createBookingUsesConfiguredHttp11ClientAndPreservesJsonExchange() throws Exception {
		String requestBody = "{\"eventId\":41,\"quantity\":2}";
		booking.stubFor(post(urlEqualTo("/api/v1/bookings"))
				.withHeader("Content-Type", equalTo("application/json"))
				.withHeader("X-Idempotency-Key", equalTo("booking-789"))
				.withRequestBody(equalTo(requestBody))
				.willReturn(aResponse()
						.withStatus(409)
						.withHeader("Content-Type", "application/problem+json")
						.withHeader("X-Booking-Conflict", "sold-out")
						.withBody("{\"title\":\"Seats unavailable\"}")));

		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/v1/bookings"))
				.header("Content-Type", "application/json")
				.header("X-Idempotency-Key", "booking-789")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
		HttpResponse<String> response = send(request);

		booking.verify(postRequestedFor(urlEqualTo("/api/v1/bookings"))
				.withHeader("Content-Type", equalTo("application/json"))
				.withHeader("X-Idempotency-Key", equalTo("booking-789"))
				.withRequestBody(equalTo(requestBody)));
		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
		assertThat(response.headers().firstValue("X-Booking-Conflict")).hasValue("sold-out");
		assertThat(response.body()).isEqualTo("{\"title\":\"Seats unavailable\"}");
		assertThat(booking.getAllServeEvents()).singleElement().satisfies(serveEvent ->
				assertThat(serveEvent.getRequest().getProtocol()).isEqualTo("HTTP/1.1"));
	}

	@Test
	void listEventsPassesDownstream503WithoutFallback() throws Exception {
		inventory.stubFor(get(urlEqualTo("/api/v1/inventory/events"))
				.willReturn(aResponse()
						.withStatus(503)
						.withHeader("Retry-After", "30")
						.withBody("inventory unavailable")));

		HttpResponse<String> response = send(getRequest("/api/v1/events").build());

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.headers().firstValue("Retry-After")).hasValue("30");
		assertThat(response.body()).isEqualTo("inventory unavailable");
		inventory.verify(1, getRequestedFor(urlEqualTo("/api/v1/inventory/events")));
	}

	@Test
	void internalOrderAndUnknownRoutesStayBlockedAtGateway() throws Exception {
		assertThat(send(getRequest("/api/v1/inventory/reservations").build()).statusCode()).isEqualTo(404);
		assertThat(send(HttpRequest.newBuilder(gatewayUri("/api/v1/inventory/reservations/abc/release"))
				.POST(HttpRequest.BodyPublishers.noBody()).build()).statusCode()).isEqualTo(404);
		assertThat(send(getRequest("/api/v1/inventory/venue/2").build()).statusCode()).isEqualTo(404);
		assertThat(send(getRequest("/api/v1/orders/99").build()).statusCode()).isEqualTo(404);
		assertThat(send(getRequest("/does-not-exist").build()).statusCode()).isEqualTo(404);
		assertNoDownstreamRequests();
	}

	@Test
	void wrongMethodsOnPublicPathsStayBlockedAtGateway() throws Exception {
		HttpRequest postEvents = HttpRequest.newBuilder(gatewayUri("/api/v1/events"))
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
		HttpRequest deleteEventDetail = HttpRequest.newBuilder(gatewayUri("/api/v1/events/41"))
				.DELETE()
				.build();
		HttpRequest getBookings = getRequest("/api/v1/bookings").build();

		assertThat(send(postEvents).statusCode()).isEqualTo(404);
		assertThat(send(deleteEventDetail).statusCode()).isEqualTo(404);
		assertThat(send(getBookings).statusCode()).isEqualTo(404);
		assertNoDownstreamRequests();
	}

	@Test
	void internalInventoryRoutesStayBlockedForTheirActualMethods() throws Exception {
		HttpRequest createReservation = HttpRequest.newBuilder(gatewayUri("/api/v1/inventory/reservations"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"bookingId":"11111111-1111-1111-1111-111111111111","eventId":41,"ticketCount":2}
						"""))
				.build();
		HttpRequest releaseReservation = HttpRequest.newBuilder(
					gatewayUri("/api/v1/inventory/reservations/11111111-1111-1111-1111-111111111111/release"))
				.PUT(HttpRequest.BodyPublishers.noBody())
				.build();

		assertThat(send(createReservation).statusCode()).isEqualTo(404);
		assertThat(send(releaseReservation).statusCode()).isEqualTo(404);
		assertNoDownstreamRequests();
	}

	private void assertNoDownstreamRequests() {
		inventory.verify(0, anyRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
		booking.verify(0, anyRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
	}

	@Test
	void inventoryDocsProxyUsesOnlyThePublicDownstreamDocument() throws Exception {
		inventory.stubFor(get(urlEqualTo("/v3/api-docs/public"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"Inventory\"}}")));

		HttpResponse<String> response = send(getRequest("/docs/inventory/v3/api-docs/public").build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
		assertThat(response.body()).contains("\"title\":\"Inventory\"");
		inventory.verify(1, getRequestedFor(urlEqualTo("/v3/api-docs/public")));
	}

	@Test
	void bookingDocsProxyUsesOnlyThePublicDownstreamDocument() throws Exception {
		booking.stubFor(get(urlEqualTo("/v3/api-docs/public"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"Booking\"}}")));

		HttpResponse<String> response = send(getRequest("/docs/booking/v3/api-docs/public").build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
		assertThat(response.body()).contains("\"title\":\"Booking\"");
		booking.verify(1, getRequestedFor(urlEqualTo("/v3/api-docs/public")));
	}

	@Test
	void swaggerUiAndItsTwoDefinitionConfigurationAreAvailable() throws Exception {
		HttpResponse<String> entryPoint = send(getRequest("/swagger-ui.html").build());

		assertThat(entryPoint.statusCode()).isIn(200, 302);
		HttpResponse<String> config = send(getRequest("/v3/api-docs/swagger-config").build());
		assertThat(config.statusCode()).isEqualTo(200);

		JsonNode swaggerConfig = objectMapper.readTree(config.body());
		List<List<String>> definitions = StreamSupport.stream(swaggerConfig.path("urls").spliterator(), false)
				.map(definition -> List.of(
						definition.path("name").asText(),
						definition.path("url").asText()))
				.toList();
		assertThat(definitions).containsExactlyInAnyOrder(
				List.of("Inventory API", "/docs/inventory/v3/api-docs/public"),
				List.of("Booking API", "/docs/booking/v3/api-docs/public"));
		assertThat(swaggerConfig.path("urls.primaryName").asText()).isEqualTo("Inventory API");
		assertThat(config.body()).doesNotContain("petstore.swagger.io");
	}

	private HttpRequest.Builder getRequest(String path) {
		return HttpRequest.newBuilder(gatewayUri(path)).GET();
	}

	private URI gatewayUri(String path) {
		return URI.create("http://localhost:" + gatewayPort + path);
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
