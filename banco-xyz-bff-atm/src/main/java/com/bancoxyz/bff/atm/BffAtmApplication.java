package com.bancoxyz.bff.atm;

import com.bancoxyz.bff.common.config.BffCommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * BFF del canal cajero automatico. Expone pocas operaciones, todas criticas:
 * consulta de saldo y retiro de efectivo. Prioriza seguridad y previsibilidad
 * sobre riqueza de datos: sesiones de minutos, dispositivo autenticado,
 * timeouts cortos, respuestas minimas y auditoria de cada operacion.
 */
@SpringBootApplication
@Import(BffCommonConfig.class)
public class BffAtmApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffAtmApplication.class, args);
    }
}
