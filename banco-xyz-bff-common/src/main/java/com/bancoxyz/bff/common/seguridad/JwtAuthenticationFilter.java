package com.bancoxyz.bff.common.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Traduce el header {@code Authorization: Bearer <token>} en una autenticacion
 * de Spring Security. Si el token es invalido no escribe la respuesta de error:
 * deja el contexto vacio y delega en el {@code AuthenticationEntryPoint} del
 * canal, de modo que todos los 401 salgan con el mismo formato.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Motivo del rechazo, para que el entry point lo pueda incluir en el 401. */
    public static final String ATRIBUTO_MOTIVO = "bff.token.motivoRechazo";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extraerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UsuarioCanal usuario = jwtService.validar(token, TipoToken.ACCESO);
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        usuario,
                        null,
                        List.of(new SimpleGrantedAuthority(usuario.canal().rol())));
                autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            } catch (TokenInvalidoException ex) {
                log.debug("Token rechazado en {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
                request.setAttribute(ATRIBUTO_MOTIVO, ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extraerToken(HttpServletRequest request) {
        String cabecera = request.getHeader("Authorization");
        if (StringUtils.hasText(cabecera) && cabecera.startsWith(PREFIJO_BEARER)) {
            String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
