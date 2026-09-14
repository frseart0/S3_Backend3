package com.bancoxyz.bff.web.config;

import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import com.bancoxyz.bff.common.seguridad.CadenaSeguridadBff;
import com.bancoxyz.bff.common.seguridad.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Seguridad del canal web. Es el unico canal que necesita CORS, porque es el
 * unico cuyo cliente es un navegador: la app movil y el cajero no estan sujetos
 * a la politica de mismo origen.
 */
@Configuration
public class SeguridadWebConfig {

    @Bean
    public SecurityFilterChain cadenaWeb(HttpSecurity http,
                                         JwtService jwtService,
                                         RespuestasSeguridadJson respuestas,
                                         CorsConfigurationSource corsSource) throws Exception {
        return CadenaSeguridadBff.base(http, jwtService, respuestas,
                        "/api/web/auth/login",
                        "/actuator/health",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .cors(cors -> cors.configurationSource(corsSource))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsSource(WebProperties propiedades) {
        var configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.origenesPermitidos());
        configuracion.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracion.setMaxAge(3600L);

        var fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/web/**", configuracion);
        return fuente;
    }
}
