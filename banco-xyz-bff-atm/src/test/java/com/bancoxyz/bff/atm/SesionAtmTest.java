package com.bancoxyz.bff.atm;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtProperties;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SaldoCuenta;
import com.bancoxyz.domain.contract.SolicitudPin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SesionAtmTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private CoreApiClient core;

    @Test
    void abreSesionConDispositivoTarjetaYPin() throws Exception {
        when(core.validarPin(any())).thenReturn(new ResultadoAutenticacion(
                true, "jane.smith", 106L, "Jane Smith", List.of("atm"), false, null, null));

        mockMvc.perform(post("/api/atm/auth/sesion")
                        .header("X-Device-Id", "ATM-001")
                        .header("X-Device-Key", "llave-atm-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarjeta":"4051000000000106","pin":"1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.dispositivoId").value("ATM-001"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(180))
                // Solo el primer nombre: un cajero es una pantalla publica.
                .andExpect(jsonPath("$.saludo").value("Jane"));

        verify(core).validarPin(new SolicitudPin(
                "4051000000000106", "1234", "ATM-001", "llave-atm-001"));
    }

    /** Un PIN mal formado no debe gastar uno de los intentos que cuenta el nucleo. */
    @Test
    void rechazaUnPinMalFormadoSinConsultarAlNucleo() throws Exception {
        mockMvc.perform(post("/api/atm/auth/sesion")
                        .header("X-Device-Id", "ATM-001")
                        .header("X-Device-Key", "llave-atm-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarjeta":"4051000000000106","pin":"12"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        verify(core, never()).validarPin(any());
    }

    @Test
    void devuelve423CuandoLaTarjetaEstaBloqueada() throws Exception {
        when(core.validarPin(any())).thenReturn(
                ResultadoAutenticacion.bloqueado("La tarjeta esta bloqueada, acuda a una sucursal"));

        mockMvc.perform(post("/api/atm/auth/sesion")
                        .header("X-Device-Id", "ATM-001")
                        .header("X-Device-Key", "llave-atm-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarjeta":"4051000000000106","pin":"9999"}"""))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("IDENTIDAD_BLOQUEADA"));
    }

    @Test
    void informaLosIntentosRestantesAlFallarElPin() throws Exception {
        when(core.validarPin(any())).thenReturn(new ResultadoAutenticacion(
                false, null, null, null, List.of(), false, 2, "PIN incorrecto"));

        mockMvc.perform(post("/api/atm/auth/sesion")
                        .header("X-Device-Id", "ATM-001")
                        .header("X-Device-Key", "llave-atm-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarjeta":"4051000000000106","pin":"9999"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("intentos restantes: 2")));
    }

    /**
     * Al expulsar la tarjeta el cajero cierra la sesion, y el token que quedo
     * en la maquina deja de servir aunque todavia no haya expirado.
     */
    @Test
    void alCerrarLaSesionElTokenDejaDeServir() throws Exception {
        String token = jwtService.emitirAcceso(new UsuarioCanal(
                "jane.smith", 106L, "Jane Smith", Canal.ATM, "ATM-001"));
        when(core.saldo(106L)).thenReturn(
                new SaldoCuenta(106L, new BigDecimal("5035.00"), Instant.parse("2026-01-01T10:00:00Z")));

        mockMvc.perform(get("/api/atm/saldo")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "ATM-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponible").value(5035));

        mockMvc.perform(post("/api/atm/sesion/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "ATM-001"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/atm/saldo")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "ATM-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("ya fue cerrada")));
    }

    @Test
    void rechazaUnTokenDeOtroCanal() throws Exception {
        var emisorWeb = new JwtService(new JwtProperties(
                jwtProperties.secreto(), jwtProperties.emisor(), Canal.WEB,
                jwtProperties.duracionAcceso(), null));
        String tokenWeb = emisorWeb.emitirAcceso(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.WEB));

        mockMvc.perform(get("/api/atm/saldo")
                        .header("Authorization", "Bearer " + tokenWeb)
                        .header("X-Device-Id", "ATM-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("no fue emitido para el canal atm")));
    }
}
