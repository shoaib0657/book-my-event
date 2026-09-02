package com.shoaib.bookmyevent.bookingservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Bean
    GroupedOpenApi internalOpenApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    GroupedOpenApi publicOpenApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/bookings")
                .addOpenApiCustomizer(openApi -> {
                    openApi.setServers(List.of(new Server().url("/")));
                    final Components components = openApi.getComponents() == null
                            ? new Components()
                            : openApi.getComponents();
                    components.addSecuritySchemes(
                            "bearerAuth",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT"));
                    openApi.setComponents(components);
                    openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                })
                .build();
    }
}
