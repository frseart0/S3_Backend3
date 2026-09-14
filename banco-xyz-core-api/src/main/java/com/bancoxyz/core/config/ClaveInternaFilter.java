package com.bancoxyz.core.config;

import com.bancoxyz.core.error.ErrorRespuesta;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Autenticacion servicio a servicio de {@code /internal/**}. La comparacion es
 * de tiempo constante para no filtrar la clave y el canal de origen se registra
 * para trazabilidad, de modo que cualquier operacion del nucleo se pueda
 * atribuir al BFF que la pidio.
 */
public class ClaveInternaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClaveInternaFilter.class);
    private static final String CABECERA_CLAVE = "X-Internal-Key";
    private static final String CABECERA_CANAL = "X-Channel";

    private final byte[] claveEsperada;
    private final ObjectMapper objectMapper;

    public ClaveInternaFilter(SeguridadInternaProperties propiedades, ObjectMapper objectMapper) {
        this.claveEsperada = propiedades.claveInterna().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clave = request.getHeader(CABECERA_CLAVE);
        if (clave == null || !MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8), claveEsperada)) {
            log.warn("Llamada rechazada a {} {}: clave interna ausente o incorrecta",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ErrorRespuesta.de(
                    "CLAVE_INTERNA_INVALIDA",
                    "El core-api solo atiende a los BFF autorizados"));
            return;
        }

        log.debug("{} {} atendido para el canal {}", request.getMethod(), request.getRequestURI(),
                Objects.requireNonNullElse(request.getHeader(CABECERA_CANAL), "desconocido"));
        filterChain.doFilter(request, response);
    }
}
