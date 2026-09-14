package com.bancoxyz.bff.atm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AtmProperties.class)
public class AtmAppConfig {

    @Bean
    public OpenAPI bffAtmOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Banco XYZ - BFF Cajeros")
                .version("1.0.0")
                .description("""
                        Backend for Frontend del canal cajero automatico. Interfaz minima para
                        operaciones criticas: consulta de saldo y retiro de efectivo. Exige
                        dispositivo registrado (X-Device-Id / X-Device-Key), tarjeta + PIN y un
                        token con audiencia 'atm' de vigencia corta."""));
    }
}
