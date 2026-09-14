package com.bancoxyz.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el filtro de clave interna solo sobre {@code /internal/*}, para que
 * Swagger UI y el endpoint de salud sigan siendo accesibles sin clave.
 */
@Configuration
public class FiltrosConfig {

    @Bean
    public FilterRegistrationBean<ClaveInternaFilter> claveInternaFilter(
            SeguridadInternaProperties propiedades, ObjectMapper objectMapper) {

        var registro = new FilterRegistrationBean<>(new ClaveInternaFilter(propiedades, objectMapper));
        registro.addUrlPatterns("/internal/*");
        registro.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE + 10);
        registro.setName("claveInternaFilter");
        return registro;
    }
}
