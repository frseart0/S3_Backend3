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
                .title("Banco XYZ - reportes del batch")
                .version("1.0.0")
                .description("""
                        Consultas de los reportes generados por los Jobs de Spring Batch.
                        Clientes, cuentas y pagos tienen su propio servicio. Los BFF deben
                        enviar la cabecera X-Internal-Key."""));
    }
}
