package com.bancoxyz.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coreApiOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Banco XYZ - core-api")
                .version("1.0.0")
                .description("""
                        API de dominio sobre los datos generados por los Jobs de Spring Batch.
                        No la consumen clientes finales: solo los BFF web, movil y cajero, que
                        deben enviar la cabecera X-Internal-Key."""));
    }
}
