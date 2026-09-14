package com.bancoxyz.bff.atm.config;

import com.bancoxyz.bff.atm.seguridad.SesionCerradaFilter;
import com.bancoxyz.bff.atm.seguridad.SesionesCerradas;
import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import com.bancoxyz.bff.common.seguridad.CadenaSeguridadBff;
import com.bancoxyz.bff.common.seguridad.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad del canal cajero. Sobre la base comun agrega el control de
 * sesiones cerradas y no habilita CORS ni refresco: un cajero no es un
 * navegador y una sesion vencida obliga a pasar la tarjeta de nuevo.
 */
@Configuration
public class SeguridadAtmConfig {

    @Bean
    public SecurityFilterChain cadenaCajero(HttpSecurity http,
                                            JwtService jwtService,
                                            RespuestasSeguridadJson respuestas,
                                            SesionesCerradas sesionesCerradas) throws Exception {
        return CadenaSeguridadBff.base(http, jwtService, respuestas,
                        "/api/atm/auth/sesion",
                        "/actuator/health",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .addFilterAfter(new SesionCerradaFilter(sesionesCerradas),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
