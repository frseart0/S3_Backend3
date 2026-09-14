package com.bancoxyz.bff.mobile;

import com.bancoxyz.bff.common.config.BffCommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * BFF del canal movil. Entrega solo lo esencial: campos minimos, listas
 * cortas, montos sin decimales, compresion y respuestas cacheables con ETag,
 * porque el cliente paga cada byte en datos moviles y bateria.
 */
@SpringBootApplication
@Import(BffCommonConfig.class)
public class BffMobileApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffMobileApplication.class, args);
    }
}
