package com.shoaib.bookmyevent.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				// The gateway is a stateless JSON API; browser sessions and CSRF tokens are not used.
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/v3/api-docs/**",
								"/docs/**",
								"/error",
								"/actuator/health",
								"/actuator/info")
						.permitAll()
						.requestMatchers("/actuator/**").authenticated()
						.requestMatchers("/internal/fallback/**").authenticated()
						.requestMatchers("/api/v1/**").authenticated()
						// Let Spring Cloud Gateway return 404 for paths it does not publish.
						.anyRequest().permitAll())
				.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
				.build();
	}
}
