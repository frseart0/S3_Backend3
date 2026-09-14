package com.bancoxyz.core.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErroresCore {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresCore.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuesta> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorRespuesta.de("RECURSO_NO_ENCONTRADO", ex.getMessage()));
    }

    /**
     * 409 y no 422: el problema no es la forma de la solicitud sino el estado
     * de la cuenta en este momento (saldo, limite diario ya consumido).
     */
    @ExceptionHandler(OperacionRechazadaException.class)
    public ResponseEntity<ErrorRespuesta> rechazada(OperacionRechazadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorRespuesta.de(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorRespuesta> argumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de("SOLICITUD_INVALIDA", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> inesperado(Exception ex) {
        log.error("Error no controlado en el core-api", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorRespuesta.de("ERROR_INTERNO", "Ocurrio un error inesperado en el nucleo"));
    }
}
