package com.paycore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "BearerAuth";
    public static final String API_KEY_AUTH = "ApiKeyAuth";

    @Bean
    public OpenAPI payCoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayCore — Payment Processing Engine API")
                        .description("Enterprise REST APIs for merchant payments, idempotency handling, refunds, webhooks, and reconciliation.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("PayCore Engineering")
                                .email("engineering@paycore.internal"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Access Token for Dashboard and Portal users"))
                        .addSecuritySchemes(API_KEY_AUTH, new SecurityScheme()
                                .name("X-Api-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Merchant Secret Key (sk_live_... / sk_test_...) for programmatic API integration")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_AUTH));
    }
}
