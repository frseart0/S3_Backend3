package com.bancoxyz.bff.mobile.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
@EnableConfigurationProperties(MobileProperties.class)
public class MobileAppConfig {

    /**
     * ETag sobre las respuestas del canal: si el saldo no cambio, la app recibe
     * un 304 sin cuerpo en vez del JSON completo. Es la optimizacion de ancho
     * de banda mas efectiva para una pantalla que se refresca cada vez que el
     * usuario abre la app.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        var registro = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registro.addUrlPatterns("/api/mobile/inicio", "/api/mobile/saldo", "/api/mobile/movimientos");
        registro.setName("etagFilter");
        return registro;
    }

    @Bean
    public OpenAPI bffMobileOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Banco XYZ - BFF Movil")
                .version("1.0.0")
                .description("""
                        Backend for Frontend del canal movil. Respuestas minimas, comprimidas y
                        cacheables con ETag. Requiere un token con audiencia 'mobile': los
                        tokens de los canales web y cajero son rechazados."""));
    }
}
