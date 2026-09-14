package com.bancoxyz.bff.web;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.common.seguridad.JwtProperties;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.TipoCuenta;
import com.bancoxyz.domain.contract.DetalleInteres;
import com.bancoxyz.domain.contract.EstadoCuentaAnual;
import com.bancoxyz.domain.contract.Movimiento;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.PerfilCuenta;
import com.bancoxyz.domain.contract.SaldoCuenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardWebTest {

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
        token = jwtService.emitirAcceso(UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.WEB));

        when(core.perfil(106L)).thenReturn(
                new PerfilCuenta(106L, "Jane Smith", TipoCuenta.AHORRO, "VALIDA", null));
        when(core.saldo(106L)).thenReturn(
                new SaldoCuenta(106L, new BigDecimal("7035.00"), Instant.parse("2026-01-01T10:00:00Z")));
        when(core.movimientos(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PaginaMovimientos(List.of(
                        new Movimiento(1L, 106L, LocalDate.of(2024, 12, 16), "deposito",
                                new BigDecimal("2500.00"), "Ingreso mensual", "VALIDA", null,
                                "cuentas-anuales-partition-1"),
                        new Movimiento(2L, 106L, LocalDate.of(2024, 12, 7), "retiro",
                                new BigDecimal("-2000.00"), "Compra", "ANOMALIA", "Tipo desconocido",
                                "cuentas-anuales-partition-2")),
                        0, 20, 58L));
        when(core.intereses(106L)).thenReturn(
                new DetalleInteres(106L, TipoCuenta.AHORRO, new BigDecimal("7000.00"),
                        new BigDecimal("0.0050"), new BigDecimal("35.00"), new BigDecimal("7035.00"),
                        40, "VALIDA", null));
        when(core.estadosAnuales(106L)).thenReturn(List.of(
                new EstadoCuentaAnual(106L, 2024, new BigDecimal("10500.00"), new BigDecimal("18900.00"),
                        new BigDecimal("-8400.00"), 58, 6)));
    }

    @Test
    void componeTodaLaPantallaEnUnaSolaLlamada() throws Exception {
        mockMvc.perform(get("/api/web/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.tipoDescripcion").value("Cuenta de ahorro"))
                .andExpect(jsonPath("$.saldo.saldo").value(7035.00))
                .andExpect(jsonPath("$.saldo.moneda").value("CLP"))
                .andExpect(jsonPath("$.ultimosMovimientos.length()").value(2))
                .andExpect(jsonPath("$.intereses.tasaAplicadaPorcentaje").value(0.50))
                .andExpect(jsonPath("$.estadosAnuales[0].anio").value(2024))
                .andExpect(jsonPath("$.resumen.totalMovimientos").value(58))
                .andExpect(jsonPath("$.resumen.totalIngresos").value(2500.00))
                .andExpect(jsonPath("$.resumen.totalEgresos").value(2000.00))
                .andExpect(jsonPath("$.resumen.movimientosConAnomalia").value(1));
    }

    /** El canal web si expone los metadatos de auditoria de la migracion batch. */
    @Test
    void incluyeLosMetadatosDeAuditoriaDelBatch() throws Exception {
        mockMvc.perform(get("/api/web/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimosMovimientos[1].estadoRegistro").value("ANOMALIA"))
                .andExpect(jsonPath("$.ultimosMovimientos[1].motivo").value("Tipo desconocido"))
                .andExpect(jsonPath("$.ultimosMovimientos[1].procesadoPorHilo")
                        .value("cuentas-anuales-partition-2"))
                .andExpect(jsonPath("$.ultimosMovimientos[1].salidaDeDinero").value(true));
    }

    /**
     * Si el dataset legacy no dejo intereses confiables para la cuenta, el
     * dashboard se entrega igual sin esa seccion.
     */
    @Test
    void omiteLasSeccionesQueElNucleoNoTiene() throws Exception {
        when(core.intereses(106L)).thenThrow(
                new CoreApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", "Sin intereses"));
        when(core.estadosAnuales(106L)).thenThrow(
                new CoreApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", "Sin estados"));

        mockMvc.perform(get("/api/web/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.cuentaId").value(106))
                .andExpect(jsonPath("$.intereses").doesNotExist())
                .andExpect(jsonPath("$.estadosAnuales.length()").value(0));
    }

    /** Un fallo real del nucleo no se disfraza de 500 del BFF. */
    @Test
    void propagaComo502UnFalloDelNucleo() throws Exception {
        when(core.saldo(106L)).thenThrow(
                new CoreApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Nucleo caido"));

        mockMvc.perform(get("/api/web/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("CORE_NO_DISPONIBLE"));
    }

    @Test
    void exigeTokenParaEntrarAlDashboard() throws Exception {
        mockMvc.perform(get("/api/web/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * Token firmado con el mismo secreto y el mismo emisor que usa este BFF,
     * pero con audiencia 'mobile': el canal web debe rechazarlo.
     */
    @Test
    void rechazaUnTokenDeOtroCanal() throws Exception {
        var emisorMovil = new JwtService(new JwtProperties(
                jwtProperties.secreto(), jwtProperties.emisor(), Canal.MOBILE,
                jwtProperties.duracionAcceso(), null));
        String tokenMovil = emisorMovil.emitirAcceso(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.MOBILE));

        mockMvc.perform(get("/api/web/dashboard").header("Authorization", "Bearer " + tokenMovil))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("no fue emitido para el canal web")));
    }
}
