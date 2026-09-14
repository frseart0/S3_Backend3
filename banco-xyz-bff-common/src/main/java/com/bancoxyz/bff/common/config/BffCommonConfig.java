package com.bancoxyz.bff.common.config;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.core.CoreApiProperties;
import com.bancoxyz.bff.common.error.ManejadorErroresBff;
import com.bancoxyz.bff.common.error.RespuestasSeguridadJson;
import com.bancoxyz.bff.common.seguridad.JwtProperties;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infraestructura que los tres BFF comparten. Se importa explicitamente desde
 * cada aplicacion ({@code @Import(BffCommonConfig.class)}) en lugar de
 * escanearse, para que quede visible que cada canal se construye a partir de
 * las mismas piezas pero con su propia configuracion.
 *
 * <p>El filtro JWT no se declara aqui a proposito: si fuera un bean de tipo
 * {@code Filter}, Spring Boot lo registraria tambien en el contenedor de
 * servlets, fuera de la cadena de Spring Security. Cada BFF lo instancia
 * dentro de su propia {@code SecurityFilterChain}.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, CoreApiProperties.class})
public class BffCommonConfig {

    @Bean
    public JwtService jwtService(JwtProperties propiedades) {
        return new JwtService(propiedades);
    }

    @Bean
    public CoreApiClient coreApiClient(CoreApiProperties propiedades,
                                       JwtProperties jwtProperties,
                                       ObjectMapper objectMapper) {
        return new CoreApiClient(propiedades, jwtProperties.canal(), objectMapper);
    }

    @Bean
    public RespuestasSeguridadJson respuestasSeguridadJson(ObjectMapper objectMapper) {
        return new RespuestasSeguridadJson(objectMapper);
    }

    @Bean
    public ManejadorErroresBff manejadorErroresBff() {
        return new ManejadorErroresBff();
    }
}
