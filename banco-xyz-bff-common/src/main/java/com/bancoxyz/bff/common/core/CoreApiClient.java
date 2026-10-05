package com.bancoxyz.bff.common.core;

import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.DetalleInteres;
import com.bancoxyz.domain.contract.EstadoCuentaAnual;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.PerfilCuenta;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.ResumenDiario;
import com.bancoxyz.domain.contract.SaldoCuenta;
import com.bancoxyz.domain.contract.SolicitudCredenciales;
import com.bancoxyz.domain.contract.SolicitudPin;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import com.bancoxyz.domain.contract.TransaccionDiaria;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Unico punto de entrada de los BFF al core-api. Centraliza la cabecera
 * {@code X-Internal-Key}, el {@code X-Channel} de trazabilidad, los timeouts y
 * la traduccion de errores HTTP a {@link CoreApiException}, para que cada BFF
 * se dedique solo a darle forma a los datos de su cliente.
 */
public class CoreApiClient {

    private static final String CABECERA_CLAVE_INTERNA = "X-Internal-Key";
    private static final String CABECERA_CANAL = "X-Channel";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Canal canal;

    public CoreApiClient(CoreApiProperties propiedades,
                         Canal canal,
                         ObjectMapper objectMapper,
                         ObjectProvider<DiscoveryClient> descubrimientos) {
        this.canal = canal;
        this.objectMapper = objectMapper;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) propiedades.timeoutConexion().toMillis());
        factory.setReadTimeout((int) propiedades.timeoutLectura().toMillis());

        var builder = RestClient.builder()
                .baseUrl(propiedades.baseUrl())
                .requestFactory(factory)
                .defaultHeader(CABECERA_CLAVE_INTERNA, propiedades.claveInterna())
                .defaultHeader(CABECERA_CANAL, canal.codigo())
                .defaultStatusHandler(HttpStatusCode::isError, (peticion, respuesta) -> {
                    throw traducirError(respuesta.getStatusCode(), respuesta.getBody());
                });
        if (hayQueDescubrir(propiedades.baseUrl())) {
            DiscoveryClient discovery = descubrimientos.getIfAvailable();
            if (discovery == null) {
                throw new IllegalStateException(
                        "bff.core-api.base-url apunta a un servicio de Eureka pero el discovery no esta habilitado");
            }
            builder.requestInterceptor(new InterceptorDescubrimiento(discovery));
        }
        this.restClient = builder.build();
    }

    private static boolean hayQueDescubrir(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String host = URI.create(baseUrl).getHost();
        return host != null && !InterceptorDescubrimiento.esDirecto(host);
    }

    // ----------------------------------------------------------------- auth

    public ResultadoAutenticacion validarCredenciales(String usuario, String password) {
        return restClient.post()
                .uri("/internal/auth/credenciales")
                .body(new SolicitudCredenciales(usuario, password, canal.codigo()))
                .retrieve()
                .body(ResultadoAutenticacion.class);
    }

    public ResultadoAutenticacion validarPin(SolicitudPin solicitud) {
        return restClient.post()
                .uri("/internal/auth/pin")
                .body(solicitud)
                .retrieve()
                .body(ResultadoAutenticacion.class);
    }

    // --------------------------------------------------------------- cuenta

    public PerfilCuenta perfil(Long cuentaId) {
        return restClient.get()
                .uri("/internal/cuentas/{id}/perfil", cuentaId)
                .retrieve()
                .body(PerfilCuenta.class);
    }

    public SaldoCuenta saldo(Long cuentaId) {
        return restClient.get()
                .uri("/internal/cuentas/{id}/saldo", cuentaId)
                .retrieve()
                .body(SaldoCuenta.class);
    }

    public PaginaMovimientos movimientos(Long cuentaId,
                                         LocalDate desde,
                                         LocalDate hasta,
                                         int pagina,
                                         int tamano) {
        return restClient.get()
                .uri(constructor -> constructor
                        .path("/internal/cuentas/{id}/movimientos")
                        .queryParamIfPresent("desde", Optional.ofNullable(desde))
                        .queryParamIfPresent("hasta", Optional.ofNullable(hasta))
                        .queryParam("pagina", pagina)
                        .queryParam("tamano", tamano)
                        .build(cuentaId))
                .retrieve()
                .body(PaginaMovimientos.class);
    }

    public DetalleInteres intereses(Long cuentaId) {
        return restClient.get()
                .uri("/internal/cuentas/{id}/intereses", cuentaId)
                .retrieve()
                .body(DetalleInteres.class);
    }

    public List<EstadoCuentaAnual> estadosAnuales(Long cuentaId) {
        return restClient.get()
                .uri("/internal/cuentas/{id}/estados-anuales", cuentaId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public ComprobanteRetiro retirar(Long cuentaId, SolicitudRetiro solicitud) {
        return restClient.post()
                .uri("/internal/cuentas/{id}/retiros", cuentaId)
                .body(solicitud)
                .retrieve()
                .body(ComprobanteRetiro.class);
    }

    // -------------------------------------------------------------- reportes

    public List<TransaccionDiaria> transacciones(LocalDate fecha, String estado, int limite) {
        return restClient.get()
                .uri(constructor -> constructor
                        .path("/internal/transacciones")
                        .queryParamIfPresent("fecha", Optional.ofNullable(fecha))
                        .queryParamIfPresent("estado", Optional.ofNullable(estado))
                        .queryParam("limite", limite)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<ResumenDiario> resumenDiario(LocalDate fecha) {
        return restClient.get()
                .uri(constructor -> constructor
                        .path("/internal/reportes/resumen-diario")
                        .queryParamIfPresent("fecha", Optional.ofNullable(fecha))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private CoreApiException traducirError(HttpStatusCode estado, InputStream cuerpo) {
        String codigo = "CORE_ERROR";
        String mensaje = "El core-api respondio " + estado.value();
        try {
            JsonNode json = objectMapper.readTree(cuerpo);
            if (json.hasNonNull("codigo")) {
                codigo = json.get("codigo").asText();
            }
            if (json.hasNonNull("mensaje")) {
                mensaje = json.get("mensaje").asText();
            }
        } catch (IOException | RuntimeException ex) {
            // Un cuerpo ilegible no debe tapar el error real del core.
        }
        return new CoreApiException(estado, codigo, mensaje);
    }
}
