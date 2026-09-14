package com.bancoxyz.domain.contract;

import java.util.List;

/** Pagina de movimientos: el canal web pagina, el movil solo pide los primeros. */
public record PaginaMovimientos(
        List<Movimiento> contenido,
        int pagina,
        int tamano,
        long totalElementos) {

    public int totalPaginas() {
        return tamano <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
    }
}
