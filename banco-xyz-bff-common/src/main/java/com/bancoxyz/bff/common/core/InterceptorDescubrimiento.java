package com.bancoxyz.bff.common.core;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resuelve el host de {@code bff.core-api.base-url} contra Eureka cuando no es
 * localhost ni una IP. Asi los BFF encuentran al core-api por su nombre de
 * servicio.
 */
public class InterceptorDescubrimiento implements ClientHttpRequestInterceptor {

    private final DiscoveryClient discovery;

    public InterceptorDescubrimiento(DiscoveryClient discovery) {
        this.discovery = discovery;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI original = request.getURI();
        String host = original.getHost();
        if (discovery == null || host == null || esDirecto(host)) {
            return execution.execute(request, body);
        }
        List<ServiceInstance> instancias = discovery.getInstances(host);
        if (instancias.isEmpty()) {
            throw new IOException("No hay instancias de " + host + " en Eureka");
        }
        ServiceInstance elegida = instancias.get(ThreadLocalRandom.current().nextInt(instancias.size()));
        URI resuelta = UriComponentsBuilder.fromUri(original)
                .scheme(elegida.isSecure() ? "https" : "http")
                .host(elegida.getHost())
                .port(elegida.getPort())
                .build(true)
                .toUri();
        return execution.execute(new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return resuelta;
            }
        }, body);
    }

    public static boolean esDirecto(String host) {
        return "localhost".equalsIgnoreCase(host) || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }
}
