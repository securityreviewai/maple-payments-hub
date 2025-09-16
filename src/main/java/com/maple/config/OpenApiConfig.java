package com.maple.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 configuration for the Maple Payments Hub API.
 * 
 * Generates comprehensive API documentation with OAuth2 security schemes
 * and detailed endpoint descriptions for payment operations.
 */
@Configuration
public class OpenApiConfig {

    @Value("${maple.api.version:1.0.0}")
    private String apiVersion;

    @Value("${maple.api.title:Maple Payments Hub API}")
    private String apiTitle;

    @Value("${maple.api.description:Corporate Payment Initiation and Processing System}")
    private String apiDescription;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(apiTitle)
                        .version(apiVersion)
                        .description(apiDescription + "\n\n" +
                                "The Maple Payments Hub provides a comprehensive API for corporate payment initiation, " +
                                "approval workflows, and settlement processing. This system supports:\n\n" +
                                "- **Payment Lifecycle Management**: Create, approve, and process payments\n" +
                                "- **Role-Based Access Control**: Treasury operations, managers, clearing, and audit roles\n" +
                                "- **ISO20022 Standards**: Industry standard payment messaging\n" +
                                "- **Audit Trail**: Comprehensive logging of all payment activities\n" +
                                "- **SFTP Integration**: Secure file transfer for payment batches\n" +
                                "- **Webhook Support**: Real-time notifications from payment networks\n\n" +
                                "## Authentication\n\n" +
                                "All API endpoints require OAuth2 JWT authentication with appropriate scopes. " +
                                "Contact your system administrator for access credentials.\n\n" +
                                "## Idempotency\n\n" +
                                "Payment submission and webhook endpoints support idempotency using the " +
                                "`Idempotency-Key` header to prevent duplicate processing."
                        )
                        .contact(new Contact()
                                .name("Maple Payments Team")
                                .email("payments-team@mapleco.com")
                                .url("https://payments.mapleco.com")
                        )
                        .license(new License()
                                .name("Proprietary")
                                .url("https://mapleco.com/license")
                        )
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Development server"),
                        new Server()
                                .url("https://api.payments.mapleco.com")
                                .description("Production server")
                ))
                .addSecurityItem(new SecurityRequirement()
                        .addList("OAuth2")
                        .addList("BearerAuth")
                )
                .components(new Components()
                        .addSecuritySchemes("OAuth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("OAuth2 authentication with JWT tokens")
                                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                                        .clientCredentials(new io.swagger.v3.oas.models.security.OAuthFlow()
                                                .tokenUrl("http://localhost:8080/auth/realms/maple/protocol/openid-connect/token")
                                                .scopes(new io.swagger.v3.oas.models.security.Scopes()
                                                        .addString("payments:read", "Read payment information")
                                                        .addString("payments:write", "Create and modify payments")
                                                        .addString("approval:write", "Approve or reject payments")
                                                        .addString("clearing:write", "Submit payments to clearing network")
                                                        .addString("audit:read", "Access audit information")
                                                )
                                        )
                                )
                        )
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer token authentication")
                        )
                        .addSchemas("PaymentStatus", new io.swagger.v3.oas.models.media.Schema<>()
                                .type("string")
                                .description("Payment lifecycle status")
                                ._enum(List.of(
                                        "CREATED", "PENDING_APPROVAL", "APPROVED", 
                                        "REJECTED", "SUBMITTED", "SETTLED", 
                                        "FAILED", "CANCELLED"
                                ))
                        )
                        .addSchemas("Currency", new io.swagger.v3.oas.models.media.Schema<>()
                                .type("string")
                                .description("ISO 4217 currency code")
                                .pattern("^[A-Z]{3}$")
                                .example("USD")
                        )
                );
    }
}
