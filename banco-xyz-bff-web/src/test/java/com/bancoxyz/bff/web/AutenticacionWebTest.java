package com.bancoxyz.bff.web;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoreApiClient core;

    @Test
    void emiteUnTokenCuandoElNucleoConfirmaLasCredenciales() throws Exception {
        when(core.validarCredenciales(eq("jane.smith"), any()))
                .thenReturn(new ResultadoAutenticacion(true, "jane.smith", 106L, "Jane Smith",
                        List.of("web", "mobile"), false, null, null));

        mockMvc.perform(post("/api/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"jane.smith","password":"Banco2026*"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.canal").value("web"))
                .andExpect(jsonPath("$.cuentaId").value(106))
                .andExpect(jsonPath("$.expiraEnSegundos").value(1800));
    }

    @Test
    void devuelve401CuandoElNucleoRechazaLasCredenciales() throws Exception {
        when(core.validarCredenciales(any(), any()))
                .thenReturn(ResultadoAutenticacion.rechazo("Credenciales incorrectas"));

        mockMvc.perform(post("/api/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"jane.smith","password":"incorrecta"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    /** Una identidad bloqueada no es un reintento posible: 423 y no 401. */
    @Test
    void devuelve423CuandoLaIdentidadEstaBloqueada() throws Exception {
        when(core.validarCredenciales(any(), any()))
                .thenReturn(ResultadoAutenticacion.bloqueado("Bloqueada tras 3 intentos fallidos"));

        mockMvc.perform(post("/api/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"jane.smith","password":"incorrecta"}"""))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("IDENTIDAD_BLOQUEADA"));
    }

    @Test
    void devuelve400CuandoFaltanCampos() throws Exception {
        mockMvc.perform(post("/api/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles").isArray());
    }
}
