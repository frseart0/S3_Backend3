package com.bancoxyz.core.eventos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.eventos")
public record EventosProperties(
        boolean habilitado,
        String topico,
        long intervaloMs) {

    public EventosProperties {
        if (topico == null || topico.isBlank()) {
            topico = "banco.retiros.realizados";
        }
        if (intervaloMs <= 0) {
            intervaloMs = 2000;
        }
    }
}
