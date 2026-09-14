package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Parte comun de la cadena de seguridad de los BFF: APIs sin estado, sin
 * formularios ni CSRF, protegidas por el token del canal. Cada BFF la completa
 * con sus rutas publicas y sus reglas propias (CORS en web, cabecera de
 * dispositivo en cajeros), de modo que solo el token del canal correcto abre
 * los endpoints de ese canal.
 */
public final class CadenaSeguridadBff {

    private CadenaSeguridadBff() {
    }

    public static HttpSecurity base(HttpSecurity http,
                                    JwtService jwtService,
                                    RespuestasSeguridadJson respuestas,
                                    String... rutasPublicas) throws Exception {
        return http
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
                        .anyRequest().hasRole(jwtService.canal().name()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
    }
}
