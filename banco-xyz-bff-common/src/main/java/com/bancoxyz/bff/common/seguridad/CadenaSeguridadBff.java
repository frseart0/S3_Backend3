package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Parte comun de la cadena de seguridad de los BFF: APIs sin estado, sin
 * formularios ni CSRF, protegidas por el JWT del canal como resource server
 * OAuth2. Cada BFF la completa con sus rutas publicas y sus reglas propias.
 */
public final class CadenaSeguridadBff {

    private CadenaSeguridadBff() {
    }

    public static HttpSecurity base(HttpSecurity http,
                                    JwtProperties jwt,
                                    ConversorJwtCanal conversor,
                                    RespuestasSeguridadJson respuestas,
                                    String... rutasPublicas) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(rutasPublicas).permitAll()
                        .anyRequest().hasRole(jwt.canal().name()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(respuestas)
                        .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(conversor)));
        return http;
    }
}
