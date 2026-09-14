package com.bancoxyz.bff.atm;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El canal cajero es el unico que mueve dinero, asi que sus validaciones se
 * prueban una por una: lo que el canal rechaza por si mismo nunca debe llegar
 * al nucleo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RetiroAtmTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CoreApiClient core;

    private String token;

    @BeforeEach
    void prepararSesion() {
        token = jwtService.emitirAcceso(new UsuarioCanal(
                "jane.smith", 106L, "Jane Smith", Canal.ATM, "ATM-001"));
    }

    @Test
    void aplicaUnRetiroValidoYDevuelveElComprobante() throws Exception {
        when(core.retirar(anyLong(), any())).thenReturn(new ComprobanteRetiro(
                "PN79Z47A", 106L, new BigDecimal("2000"), new BigDecimal("5035.00"),
                Instant.parse("2026-01-01T12:00:00Z"), false));

        mockMvc.perform(peticionRetiro("2000", "clave-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoAutorizacion").value("PN79Z47A"))
                .andExpect(jsonPath("$.montoEntregado").value(2000))
                .andExpect(jsonPath("$.saldoResultante").value(5035.00))
                .andExpect(jsonPath("$.dispositivoId").value("ATM-001"))
                .andExpect(jsonPath("$.reintento").value(false));
    }

    @Test
    void rechazaSinLlamarAlNucleoUnMontoQueNoEsMultiploDeLaDenominacion() throws Exception {
        mockMvc.perform(peticionRetiro("1500", "clave-2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DENOMINACION_INVALIDA"));

        verify(core, never()).retirar(anyLong(), any());
    }

    @Test
    void rechazaUnMontoSobreElTopeDelCanal() throws Exception {
        mockMvc.perform(peticionRetiro("500000", "clave-3"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MONTO_SOBRE_TOPE_CANAL"));

        verify(core, never()).retirar(anyLong(), any());
    }

    @Test
    void rechazaUnMontoNoPositivo() throws Exception {
        mockMvc.perform(peticionRetiro("0", "clave-4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    /** Un token emitido en otro cajero no sirve en esta maquina. */
    @Test
    void rechazaUnTokenDeOtroDispositivo() throws Exception {
        mockMvc.perform(post("/api/atm/retiros")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "ATM-999")
                        .header("Idempotency-Key", "clave-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":2000}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DISPOSITIVO_NO_COINCIDE"));
    }

    @Test
    void exigeLaClaveDeIdempotencia() throws Exception {
        mockMvc.perform(post("/api/atm/retiros")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "ATM-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":2000}"""))
                .andExpect(status().isBadRequest());
    }

    /**
     * Cuando el nucleo detecta que la orden ya estaba aplicada, el comprobante
     * llega marcado como reintento para que el cajero no entregue el efectivo
     * una segunda vez.
     */
    @Test
    void marcaComoReintentoElComprobanteDeUnaOrdenYaAplicada() throws Exception {
        when(core.retirar(anyLong(), any())).thenReturn(new ComprobanteRetiro(
                "PN79Z47A", 106L, new BigDecimal("2000"), new BigDecimal("5035.00"),
                Instant.parse("2026-01-01T12:00:00Z"), true));

        mockMvc.perform(peticionRetiro("2000", "clave-repetida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reintento").value(true))
                .andExpect(jsonPath("$.codigoAutorizacion").value("PN79Z47A"));
    }

    /** El motivo real del nucleo (saldo insuficiente) debe llegar al cliente. */
    @Test
    void propagaElRechazoDelNucleoConSuCodigo() throws Exception {
        when(core.retirar(anyLong(), any())).thenThrow(new CoreApiException(
                HttpStatus.CONFLICT, "SALDO_INSUFICIENTE", "Saldo insuficiente: disponible 1000"));

        mockMvc.perform(peticionRetiro("2000", "clave-6"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
    }

    @Test
    void enviaAlNucleoElCanalYElDispositivoDeOrigen() throws Exception {
        when(core.retirar(anyLong(), any())).thenReturn(new ComprobanteRetiro(
                "PN79Z47A", 106L, new BigDecimal("2000"), new BigDecimal("5035.00"),
                Instant.parse("2026-01-01T12:00:00Z"), false));

        mockMvc.perform(peticionRetiro("2000", "clave-7")).andExpect(status().isOk());

        verify(core).retirar(106L, new SolicitudRetiro(
                new BigDecimal("2000"), "atm", "clave-7", "ATM-001"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticionRetiro(
            String monto, String claveIdempotencia) {
        return post("/api/atm/retiros")
                .header("Authorization", "Bearer " + token)
                .header("X-Device-Id", "ATM-001")
                .header("Idempotency-Key", claveIdempotencia)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\":" + monto + "}");
    }
}
