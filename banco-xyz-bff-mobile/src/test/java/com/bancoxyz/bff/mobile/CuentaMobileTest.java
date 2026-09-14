package com.bancoxyz.bff.mobile;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtProperties;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.Movimiento;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.SaldoCuenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que define a este canal es lo que <em>no</em> viaja: aqui se verifica que
 * los metadatos de auditoria y las anomalias se queden fuera del payload.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CuentaMobileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private CoreApiClient core;

    private String token;

    @BeforeEach
    void prepararNucleo() {
        token = jwtService.emitirAcceso(UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.MOBILE));

        when(core.saldo(106L)).thenReturn(
                new SaldoCuenta(106L, new BigDecimal("7035.49"), Instant.parse("2026-01-01T10:00:00Z")));
        when(core.movimientos(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PaginaMovimientos(List.of(
                        new Movimiento(1L, 106L, LocalDate.of(2024, 12, 16), "deposito",
                                new BigDecimal("2500.00"),
                                "Ingreso mensual con una descripcion muy larga que no cabe en el telefono",
                                "VALIDA", null, "cuentas-anuales-partition-1"),
                        new Movimiento(2L, 106L, LocalDate.of(2024, 12, 10), "compra",
                                new BigDecimal("-800.00"), "Compra supermercado", "ANOMALIA",
                                "Tipo de transaccion desconocido", "cuentas-anuales-partition-2"),
                        new Movimiento(3L, 106L, LocalDate.of(2024, 12, 7), "retiro",
                                new BigDecimal("-2000.00"), "Retiro cajero", "VALIDA_CORREGIDA",
                                "Signo corregido", "cuentas-anuales-partition-3")),
                        0, 10, 58L));
    }

    @Test
    void elInicioTraeSaldoYMovimientosEnUnaLlamada() throws Exception {
        mockMvc.perform(get("/api/mobile/inicio").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Jane Smith"))
                .andExpect(jsonPath("$.saldo.saldo").value(7035))
                .andExpect(jsonPath("$.saldo.moneda").value("CLP"))
                .andExpect(jsonPath("$.movimientos.length()").value(2));
    }

    @Test
    void noEnviaMetadatosDeAuditoriaAlTelefono() throws Exception {
        mockMvc.perform(get("/api/mobile/movimientos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fecha").value("2024-12-16"))
                .andExpect(jsonPath("$[0].tipo").value("deposito"))
                .andExpect(jsonPath("$[0].monto").value(2500))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].estado").doesNotExist())
                .andExpect(jsonPath("$[0].motivo").doesNotExist())
                .andExpect(jsonPath("$[0].procesadoPorHilo").doesNotExist());
    }

    /** Una anomalia sin explicacion en una lista de telefono solo genera dudas. */
    @Test
    void dejaFueraLosMovimientosMarcadosComoAnomalia() throws Exception {
        mockMvc.perform(get("/api/mobile/movimientos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].tipo").value("retiro"));
    }

    @Test
    void acortaLasDescripcionesLargas() throws Exception {
        mockMvc.perform(get("/api/mobile/movimientos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].detalle").value(
                        org.hamcrest.Matchers.endsWith("...")))
                .andExpect(jsonPath("$[0].detalle").value(
                        org.hamcrest.Matchers.hasLength(40)));
    }

    @Test
    void entregaUnEtagParaQueLaAppPuedaCachear() throws Exception {
        mockMvc.perform(get("/api/mobile/saldo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void responde304CuandoElEtagNoCambio() throws Exception {
        String etag = mockMvc.perform(get("/api/mobile/saldo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mockMvc.perform(get("/api/mobile/saldo")
                        .header("Authorization", "Bearer " + token)
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void rechazaUnTokenDeOtroCanal() throws Exception {
        var emisorWeb = new JwtService(new JwtProperties(
                jwtProperties.secreto(), jwtProperties.emisor(), Canal.WEB,
                jwtProperties.duracionAcceso(), null));
        String tokenWeb = emisorWeb.emitirAcceso(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.WEB));

        mockMvc.perform(get("/api/mobile/saldo").header("Authorization", "Bearer " + tokenWeb))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("no fue emitido para el canal mobile")));
    }

    @Test
    void nuncaExcedeElMaximoDeMovimientosDelCanal() throws Exception {
        mockMvc.perform(get("/api/mobile/movimientos?limite=500")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
