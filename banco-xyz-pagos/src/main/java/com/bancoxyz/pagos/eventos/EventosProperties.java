package com.bancoxyz.pagos.eventos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.eventos")
public record EventosProperties(
        boolean habilitado,
        String topico,
        String topicoTransacciones,
        long intervaloMs) {

    public EventosProperties {
        if (topico == null || topico.isBlank()) {
            topico = "banco.retiros.realizados";
        }
        if (topicoTransacciones == null || topicoTransacciones.isBlank()) {
            topicoTransacciones = "banco.transacciones.completadas";
        }
        if (intervaloMs <= 0) {
            intervaloMs = 2000;
        }
    }
}
