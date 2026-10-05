package com.bancoxyz.auth.identidad;

import com.bancoxyz.auth.config.AuthProperties;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SolicitudCredenciales;
import com.bancoxyz.domain.contract.SolicitudPin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * El authorization server no guarda contrasenas. Le pregunta al core-api, que
 * es el dueno de {@code usuarios_canal}, y traduce el resultado a un token.
 */
@Component
public class CoreIdentidadClient {

    private static final String CABECERA_CLAVE = "X-Internal-Key";

    private final RestClient restClient;
    private final String baseUrl;

    public CoreIdentidadClient(AuthProperties propiedades,
                                ObjectProvider<DiscoveryClient> descubrimientos) {
        this.baseUrl = sinBarraFinal(propiedades.coreBaseUrl());

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(new DescubrimientoInterceptor(descubrimientos.getIfAvailable()))
                .defaultHeader(CABECERA_CLAVE, propiedades.claveInterna())
                .build();
    }

    public ResultadoAutenticacion credenciales(String usuario, String password, String canal) {
        return restClient.post()
                .uri(resolver("/internal/auth/credenciales"))
                .body(new SolicitudCredenciales(usuario, password, canal))
                .retrieve()
                .body(ResultadoAutenticacion.class);
    }

    public ResultadoAutenticacion pin(SolicitudPin solicitud) {
        return restClient.post()
                .uri(resolver("/internal/auth/pin"))
                .body(solicitud)
                .retrieve()
                .body(ResultadoAutenticacion.class);
    }

    private String resolver(String path) {
        return baseUrl + path;
    }

    private static String sinBarraFinal(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("auth.core-base-url es obligatorio");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
