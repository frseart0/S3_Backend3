package com.bancoxyz.bff.web.dto;

import java.util.List;

/** Pagina con los metadatos que necesita una tabla con paginador. */
public record PaginaWeb<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {
}
