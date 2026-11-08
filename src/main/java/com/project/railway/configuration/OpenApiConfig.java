package com.project.railway.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI (http://localhost:8080/swagger-ui/index.html, only when DOCS_PUBLIC=true).
 * Click "Authorize" and paste the token from /admin/login or /customer/login; Swagger then
 * sends it in the "Bearer" header the API expects.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI railwayOpenApi() {
		return new OpenAPI()
				.info(new Info().title("Railway API").version("1.5.1")
						.description("Log in with /admin/login or /customer/login, then click Authorize and paste the token."))
				.components(new Components().addSecuritySchemes("token",
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("Bearer")))
				.addSecurityItem(new SecurityRequirement().addList("token"));
	}
}
