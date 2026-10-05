package com.bancoxyz.bff.atm.config;

import com.bancoxyz.bff.atm.seguridad.SesionCerradaFilter;
import com.bancoxyz.bff.atm.seguridad.SesionesCerradas;
import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import com.bancoxyz.bff.common.seguridad.CadenaSeguridadBff;
import com.bancoxyz.bff.common.seguridad.ConversorJwtCanal;
import com.bancoxyz.bff.common.seguridad.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del canal cajero. Sobre la base comun agrega el control de
 * sesiones cerradas y no habilita CORS ni refresco: un cajero no es un
 * navegador y una sesion vencida obliga a pasar la tarjeta de nuevo.
 */
@Configuration
public class SeguridadAtmConfig {

    @Bean
    public SecurityFilterChain cadenaCajero(HttpSecurity http,
                                            JwtProperties jwt,
                                            ConversorJwtCanal conversor,
                                            RespuestasSeguridadJson respuestas,
                                            SesionesCerradas sesionesCerradas) throws Exception {
        return CadenaSeguridadBff.base(http, jwt, conversor, respuestas,
                        "/api/atm/auth/sesion",
                        "/actuator/health",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .addFilterAfter(new SesionCerradaFilter(sesionesCerradas),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }
}
