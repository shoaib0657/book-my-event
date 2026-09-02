package com.shoaib.bookmyevent.apigateway.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Local OIDC provider used by gateway integration tests. It signs real JWTs and
 * publishes discovery metadata and a JWKS endpoint without depending on Keycloak.
 */
public final class TestOidcIssuer {

	private static final String REALM_PATH = "/realms/book-my-event";

	private final WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
	private final RSAKey signingKey;

	public TestOidcIssuer() {
		try {
			signingKey = new RSAKeyGenerator(2048)
					.keyID(UUID.randomUUID().toString())
					.generate();
		} catch (JOSEException exception) {
			throw new IllegalStateException("Could not create the test signing key", exception);
		}

		server.start();
		stubProviderMetadata();
	}

	public String issuerUri() {
		return server.baseUrl() + REALM_PATH;
	}

	public String jwkSetUri() {
		return issuerUri() + "/protocol/openid-connect/certs";
	}

	public String validToken(String audience) {
		Instant now = Instant.now();
		return token(issuerUri(), audience, now, now.plus(5, ChronoUnit.MINUTES));
	}

	public String token(String issuer, String audience) {
		Instant now = Instant.now();
		return token(issuer, audience, now, now.plus(5, ChronoUnit.MINUTES));
	}

	public String expiredToken(String audience) {
		Instant now = Instant.now();
		return token(
				issuerUri(),
				audience,
				now.minus(10, ChronoUnit.MINUTES),
				now.minus(1, ChronoUnit.MINUTES));
	}

	private String token(String issuer, String audience, Instant issuedAt, Instant expiresAt) {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject("demo-user-id")
				.audience(audience)
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256)
						.type(JOSEObjectType.JWT)
						.keyID(signingKey.getKeyID())
						.build(),
				claims);

		try {
			jwt.sign(new RSASSASigner(signingKey));
		} catch (JOSEException exception) {
			throw new IllegalStateException("Could not sign a test access token", exception);
		}
		return jwt.serialize();
	}

	public void stop() {
		server.stop();
	}

	private void stubProviderMetadata() {
		server.stubFor(get(urlEqualTo(REALM_PATH + "/.well-known/openid-configuration"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"issuer":"%s","jwks_uri":"%s"}
								""".formatted(issuerUri(), jwkSetUri()))));
		server.stubFor(get(urlEqualTo(REALM_PATH + "/protocol/openid-connect/certs"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}")));
	}
}
