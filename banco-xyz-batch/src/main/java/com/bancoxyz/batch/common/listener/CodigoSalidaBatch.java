package com.bancoxyz.batch.common.listener;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Reemplaza el generador de Spring Boot, que mira la primera ejecucion y
 * devolveria fallo aunque una reejecucion posterior haya terminado bien.
 */
@Component
public class CodigoSalidaBatch implements ExitCodeGenerator {

    private final ResultadoBatch resultado;

    public CodigoSalidaBatch(ResultadoBatch resultado) {
        this.resultado = resultado;
    }

    @Override
    public int getExitCode() {
        return resultado.codigoSalida();
    }
}
