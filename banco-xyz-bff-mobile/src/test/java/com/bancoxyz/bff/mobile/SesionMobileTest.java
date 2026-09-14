package com.bancoxyz.bff.mobile;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.seguridad.JwtService;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.domain.Canal;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SesionMobileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CoreApiClient core;

    @Test
    void elLoginEntregaAccessYRefreshToken() throws Exception {
        when(core.validarCredenciales(any(), any()))
                .thenReturn(new ResultadoAutenticacion(true, "jane.smith", 106L, "Jane Smith",
                        List.of("web", "mobile"), false, null, null));

        mockMvc.perform(post("/api/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"jane.smith","password":"Banco2026*"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiraEnSegundos").value(900));
    }

    @Test
    void elRefrescoEntregaUnAccessTokenNuevoSinPedirCredenciales() throws Exception {
        String refresco = jwtService.emitirRefresco(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.MOBILE));

        String respuesta = mockMvc.perform(post("/api/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", refresco))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(respuesta);
        assertThat(json.get("token").asText()).isNotBlank();
        assertThat(json.get("refreshToken").asText()).isEqualTo(refresco);
    }

    /** Un access token no habilita a renovar la sesion: el claim de tipo lo impide. */
    @Test
    void noSePuedeRefrescarConUnAccessToken() throws Exception {
        String acceso = jwtService.emitirAcceso(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.MOBILE));

        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", acceso))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }
}
