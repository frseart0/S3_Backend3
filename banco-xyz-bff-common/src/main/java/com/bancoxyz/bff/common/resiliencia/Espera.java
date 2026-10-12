package com.bancoxyz.bff.common.resiliencia;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Desenvuelve el futuro de Resilience4j para que el manejador vea la causa real. */
public final class Espera {

    private Espera() {
    }

    public static <T> T join(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw ex;
        }
    }
}
