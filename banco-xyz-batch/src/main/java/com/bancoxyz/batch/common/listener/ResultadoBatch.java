package com.bancoxyz.batch.common.listener;

import org.springframework.batch.core.BatchStatus;
import org.springframework.stereotype.Component;

/**
 * Resultado que ve el proceso al salir. Un exito posterior a un fallo
 * (la reejecucion termino bien) no se pisa con el FAILED de la ejecucion
 * original: el codigo de salida mira el ultimo desenlace, no el primer intento.
 */
@Component
public class ResultadoBatch {

    private volatile boolean huboExito;
    private volatile boolean fracasoDefinitivo;

    public void exito() {
        huboExito = true;
        fracasoDefinitivo = false;
    }

    public void fracaso() {
        if (!huboExito) {
            fracasoDefinitivo = true;
        }
    }

    public int codigoSalida() {
        return fracasoDefinitivo ? BatchStatus.FAILED.ordinal() : 0;
    }
}
