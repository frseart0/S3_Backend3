package com.bancoxyz.bff.common.error;

import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.common.seguridad.TokenInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

/**
 * Traduce a HTTP las excepciones de los tres BFF. La regla importante es la de
 * {@link CoreApiException}: los errores de negocio del core (4xx) se propagan
 * tal cual para que el cliente vea el motivo real, mientras que los fallos de
 * infraestructura del core (5xx, timeouts) se reportan como 502/504, que es lo
 * que realmente le pasa al canal.
 */
@RestControllerAdvice
public class ManejadorErroresBff {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresBff.class);

    @ExceptionHandler(AutenticacionFallidaException.class)
    public ResponseEntity<ErrorRespuesta> autenticacion(AutenticacionFallidaException ex) {
        if (ex.bloqueado()) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(ErrorRespuesta.de("IDENTIDAD_BLOQUEADA", ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorRespuesta.de("CREDENCIALES_INVALIDAS", ex.getMessage()));
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ErrorRespuesta> token(TokenInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorRespuesta.de("TOKEN_INVALIDO", ex.getMessage()));
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<ErrorRespuesta> operacion(OperacionInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorRespuesta.de(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de("SOLICITUD_INVALIDA", "La solicitud no es valida", detalles));
    }

    /**
     * Cabecera o parametro obligatorio ausente (por ejemplo el
     * {@code Idempotency-Key} de un retiro) y cuerpos JSON mal formados: es un
     * error del cliente, no del servidor.
     */
    @ExceptionHandler({ServletRequestBindingException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorRespuesta> solicitudMalFormada(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de("SOLICITUD_INVALIDA", ex.getMessage()));
    }

    @ExceptionHandler(CanalNoDisponibleException.class)
    public ResponseEntity<ErrorRespuesta> canalNoDisponible(CanalNoDisponibleException ex) {
        log.warn("Canal degradado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorRespuesta.de("CAJERO_NO_DISPONIBLE", ex.getMessage()));
    }

    @ExceptionHandler(CoreApiException.class)
    public ResponseEntity<ErrorRespuesta> core(CoreApiException ex) {
        if (ex.estado().is4xxClientError()) {
            return ResponseEntity.status(ex.estado()).body(ErrorRespuesta.de(ex.codigo(), ex.getMessage()));
        }
        log.error("El core-api fallo con {}: {}", ex.estado(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorRespuesta.de("CORE_NO_DISPONIBLE",
                        "El nucleo bancario no pudo atender la solicitud"));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorRespuesta> timeout(ResourceAccessException ex) {
        log.error("Timeout o fallo de red hacia el core-api: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ErrorRespuesta.de("CORE_SIN_RESPUESTA",
                        "El nucleo bancario no respondio dentro del tiempo permitido"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> inesperado(Exception ex) {
        log.error("Error no controlado en el BFF", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorRespuesta.de("ERROR_INTERNO", "Ocurrio un error inesperado"));
    }
}
