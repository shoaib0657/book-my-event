package com.shoaib.bookmyevent.inventoryservice.config;

import io.swagger.v3.oas.models.Paths;
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
                .pathsToMatch(
                        "/api/v1/inventory/events",
                        "/api/v1/inventory/event/{eventId}")
                .addOpenApiCustomizer(openApi -> {
                    final Paths servicePaths = openApi.getPaths();
                    final Paths gatewayPaths = new Paths();
                    gatewayPaths.addPathItem("/api/v1/events",
                            servicePaths.get("/api/v1/inventory/events"));
                    gatewayPaths.addPathItem("/api/v1/events/{eventId}",
                            servicePaths.get("/api/v1/inventory/event/{eventId}"));
                    openApi.setPaths(gatewayPaths);
                    openApi.setServers(List.of(new Server().url("/")));
                })
                .build();
    }
}
