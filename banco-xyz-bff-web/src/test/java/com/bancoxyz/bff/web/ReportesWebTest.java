package com.bancoxyz.bff.web;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ResumenDiario;
import com.bancoxyz.domain.contract.TransaccionDiaria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportesWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CoreApiClient core;

    private String token;

    @BeforeEach
    void preparar() {
        token = jwtService.emitirAcceso(UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.WEB));
        when(core.transacciones(any(), any(), anyInt())).thenReturn(List.of(
                new TransaccionDiaria(1L, 9001L, LocalDate.of(2024, 3, 1),
                        new BigDecimal("1500.00"), "credito", "VALIDA", null),
                new TransaccionDiaria(2L, 9002L, LocalDate.of(2024, 3, 1),
                        new BigDecimal("0.00"), "invalid", "ANOMALIA", "Tipo desconocido")));
        when(core.resumenDiario(any())).thenReturn(List.of(
                new ResumenDiario(LocalDate.of(2024, 3, 1), 10, 7, 2, 1,
                        new BigDecimal("5000.00"), new BigDecimal("2000.00"))));
    }

    @Test
    void entregaLasTransaccionesConMetadatosDeAuditoria() throws Exception {
        mockMvc.perform(get("/api/web/reportes/transacciones-diarias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estadoRegistro").value("VALIDA"))
                .andExpect(jsonPath("$[0].anomalia").value(false))
                .andExpect(jsonPath("$[1].estadoRegistro").value("ANOMALIA"))
                .andExpect(jsonPath("$[1].motivo").value("Tipo desconocido"))
                .andExpect(jsonPath("$[1].anomalia").value(true));
    }

    @Test
    void calculaElMontoNetoDelResumenDiario() throws Exception {
        mockMvc.perform(get("/api/web/reportes/resumen-diario")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalValidas").value(7))
                .andExpect(jsonPath("$[0].totalAnomalias").value(2))
                .andExpect(jsonPath("$[0].montoNeto").value(3000.00));
    }
}
