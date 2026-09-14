package com.bancoxyz.bff.mobile.config;

import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import com.bancoxyz.bff.common.seguridad.CadenaSeguridadBff;
import com.bancoxyz.bff.common.seguridad.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del canal movil. Sin CORS (el cliente es una app nativa, no un
 * navegador) y con el endpoint de refresco abierto: se invoca justamente
 * cuando el access token ya expiro, asi que no puede exigir uno valido.
 */
@Configuration
public class SeguridadMobileConfig {

    @Bean
    public SecurityFilterChain cadenaMovil(HttpSecurity http,
                                           JwtService jwtService,
                                           RespuestasSeguridadJson respuestas) throws Exception {
        return CadenaSeguridadBff.base(http, jwtService, respuestas,
                        "/api/mobile/auth/login",
                        "/api/mobile/auth/refresh",
                        "/actuator/health",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .build();
    }
}
