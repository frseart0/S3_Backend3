package com.bancoxyz.bff.atm.seguridad;

import com.bancoxyz.bff.common.seguridad.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Corre despues de la validacion del JWT y descarta la autenticacion si el
 * token pertenece a una sesion ya cerrada. El rechazo lo emite el entry point
 * del canal, igual que cualquier otro 401.
 */
public class SesionCerradaFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final SesionesCerradas sesionesCerradas;

    public SesionCerradaFilter(SesionesCerradas sesionesCerradas) {
        this.sesionesCerradas = sesionesCerradas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
            String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
            if (sesionesCerradas.estaCerrada(token)) {
                SecurityContextHolder.clearContext();
                request.setAttribute(JwtAuthenticationFilter.ATRIBUTO_MOTIVO,
                        "La sesion del cajero ya fue cerrada");
            }
        }
        filterChain.doFilter(request, response);
    }
}
