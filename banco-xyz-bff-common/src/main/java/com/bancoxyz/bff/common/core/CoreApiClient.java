package com.bancoxyz.bff.common.core;

import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ComprobanteOperacion;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.CuentaOperativa;
import com.bancoxyz.domain.contract.DetalleInteres;
import com.bancoxyz.domain.contract.EstadoCuentaAnual;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.PerfilCliente;
import com.bancoxyz.domain.contract.PerfilCuenta;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.ResumenDiario;
import com.bancoxyz.domain.contract.SaldoCuenta;
import com.bancoxyz.domain.contract.SolicitudAperturaCuenta;
import com.bancoxyz.domain.contract.SolicitudCredenciales;
import com.bancoxyz.domain.contract.SolicitudMantenimientoCuenta;
import com.bancoxyz.domain.contract.SolicitudMovimiento;
import com.bancoxyz.domain.contract.SolicitudPin;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import com.bancoxyz.domain.contract.SolicitudTransferencia;
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
 * Punto de entrada de los BFF a los servicios de dominio. Reportes, cuentas,
 * pagos y clientes tienen su propia URL. La cabecera {@code X-Internal-Key}
 * y la traduccion de errores son las mismas para todos.
 */
public class CoreApiClient {

    private static final String CABECERA_CLAVE_INTERNA = "X-Internal-Key";
    private static final String CABECERA_CANAL = "X-Channel";

    private final RestClient reportes;
    private final RestClient cuentas;
    private final RestClient pagos;
    private final RestClient clientes;
    private final ObjectMapper objectMapper;
    private final Canal canal;

    public CoreApiClient(CoreApiProperties propiedades,
                         Canal canal,
                         ObjectMapper objectMapper,
                         ObjectProvider<DiscoveryClient> descubrimientos) {
        this.canal = canal;
        this.objectMapper = objectMapper;
        this.reportes = cliente(propiedades.baseUrl(), propiedades, descubrimientos);
        this.cuentas = cliente(propiedades.cuentasUrl(), propiedades, descubrimientos);
        this.pagos = cliente(propiedades.pagosUrl(), propiedades, descubrimientos);
        this.clientes = cliente(propiedades.clientesUrl(), propiedades, descubrimientos);
    }

    private RestClient cliente(String baseUrl,
                               CoreApiProperties propiedades,
                               ObjectProvider<DiscoveryClient> descubrimientos) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) propiedades.timeoutConexion().toMillis());
        factory.setReadTimeout((int) propiedades.timeoutLectura().toMillis());

        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(CABECERA_CLAVE_INTERNA, propiedades.claveInterna())
                .defaultHeader(CABECERA_CANAL, canal.codigo())
                .defaultStatusHandler(HttpStatusCode::isError, (peticion, respuesta) -> {
                    throw traducirError(respuesta.getStatusCode(), respuesta.getBody());
                });
        if (hayQueDescubrir(baseUrl)) {
            DiscoveryClient discovery = descubrimientos.getIfAvailable();
            if (discovery == null) {
                throw new IllegalStateException(
                        "La URL " + baseUrl + " apunta a un servicio de Eureka pero el discovery no esta habilitado");
            }
            builder.requestInterceptor(new InterceptorDescubrimiento(discovery));
        }
        return builder.build();
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
        return clientes.post()
                .uri("/internal/auth/credenciales")
                .body(new SolicitudCredenciales(usuario, password, canal.codigo()))
                .retrieve()
                .body(ResultadoAutenticacion.class);
    }

    public ResultadoAutenticacion validarPin(SolicitudPin solicitud) {
        return clientes.post()
                .uri("/internal/auth/pin")
                .body(solicitud)
                .retrieve()
                .body(ResultadoAutenticacion.class);
    }

    // --------------------------------------------------------------- cuenta

    public PerfilCuenta perfil(Long cuentaId) {
        return cuentas.get()
                .uri("/internal/cuentas/{id}/perfil", cuentaId)
                .retrieve()
                .body(PerfilCuenta.class);
    }

    public SaldoCuenta saldo(Long cuentaId) {
        return cuentas.get()
                .uri("/internal/cuentas/{id}/saldo", cuentaId)
                .retrieve()
                .body(SaldoCuenta.class);
    }

    public PaginaMovimientos movimientos(Long cuentaId,
                                         LocalDate desde,
                                         LocalDate hasta,
                                         int pagina,
                                         int tamano) {
        return cuentas.get()
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
        return cuentas.get()
                .uri("/internal/cuentas/{id}/intereses", cuentaId)
                .retrieve()
                .body(DetalleInteres.class);
    }

    public List<EstadoCuentaAnual> estadosAnuales(Long cuentaId) {
        return cuentas.get()
                .uri("/internal/cuentas/{id}/estados-anuales", cuentaId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public ComprobanteRetiro retirar(Long cuentaId, SolicitudRetiro solicitud) {
        return pagos.post()
                .uri("/internal/cuentas/{id}/retiros", cuentaId)
                .body(solicitud)
                .retrieve()
                .body(ComprobanteRetiro.class);
    }

    public PerfilCliente perfilCliente(String usuario) {
        return clientes.get()
                .uri("/internal/clientes/{usuario}", usuario)
                .retrieve()
                .body(PerfilCliente.class);
    }

    public ComprobanteOperacion depositar(Long cuentaId, SolicitudMovimiento solicitud) {
        return pagos.post()
                .uri("/internal/cuentas/{id}/depositos", cuentaId)
                .body(solicitud)
                .retrieve()
                .body(ComprobanteOperacion.class);
    }

    public ComprobanteOperacion pagar(Long cuentaId, SolicitudMovimiento solicitud) {
        return pagos.post()
                .uri("/internal/cuentas/{id}/pagos", cuentaId)
                .body(solicitud)
                .retrieve()
                .body(ComprobanteOperacion.class);
    }

    public ComprobanteOperacion transferir(Long cuentaId, SolicitudTransferencia solicitud) {
        return pagos.post()
                .uri("/internal/cuentas/{id}/transferencias", cuentaId)
                .body(solicitud)
                .retrieve()
                .body(ComprobanteOperacion.class);
    }

    public CuentaOperativa abrirCuenta(SolicitudAperturaCuenta solicitud) {
        return cuentas.post()
                .uri("/internal/cuentas")
                .body(solicitud)
                .retrieve()
                .body(CuentaOperativa.class);
    }

    public CuentaOperativa cerrarCuenta(Long cuentaId) {
        return cuentas.post()
                .uri("/internal/cuentas/{id}/cierre", cuentaId)
                .retrieve()
                .body(CuentaOperativa.class);
    }

    public CuentaOperativa mantenerCuenta(Long cuentaId, SolicitudMantenimientoCuenta solicitud) {
        return cuentas.patch()
                .uri("/internal/cuentas/{id}", cuentaId)
                .body(solicitud)
                .retrieve()
                .body(CuentaOperativa.class);
    }

    // -------------------------------------------------------------- reportes

    public List<TransaccionDiaria> transacciones(LocalDate fecha, String estado, int limite) {
        return reportes.get()
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
        return reportes.get()
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
