package com.bancoxyz.bff.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(WebProperties.class)
public class WebAppConfig {

    /**
     * El dashboard hace cinco llamadas al core-api que no dependen entre si.
     * Con hilos virtuales (Java 21) esperar cinco respuestas en paralelo no
     * cuesta hilos de plataforma, asi que la pantalla completa tarda lo que la
     * llamada mas lenta en vez de la suma de todas.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService ejecutorComposicion() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public OpenAPI bffWebOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Banco XYZ - BFF Web")
                .version("1.0.0")
                .description("""
                        Backend for Frontend del canal web. Entrega respuestas completas y
                        compuestas para interfaces ricas. Requiere un token con audiencia
                        'web': los tokens de los canales movil y cajero son rechazados."""));
    }
}
