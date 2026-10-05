package com.bancoxyz.auth.identidad;

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
 * Si la URL del core es un nombre de servicio, la cambia por una instancia
 * registrada en Eureka. Un host directo (localhost o IP) se deja igual.
 */
final class DescubrimientoInterceptor implements ClientHttpRequestInterceptor {

    private final DiscoveryClient discovery;

    DescubrimientoInterceptor(DiscoveryClient discovery) {
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

    static boolean esDirecto(String host) {
        return "localhost".equalsIgnoreCase(host) || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }
}
