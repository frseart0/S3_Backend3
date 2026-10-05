package com.bancoxyz.auth;

import com.bancoxyz.auth.identidad.CoreIdentidadClient;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CanalPasswordGrantTest {

    private static final String SECRETO = "secreto-oauth-banco-xyz-compartido-2026-cambiar";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoreIdentidadClient core;

    @Test
    void elGrantCanalPasswordEmiteUnJwtConLaAudienciaDelCanal() throws Exception {
        when(core.credenciales("jane.smith", "Banco2026*", "web"))
                .thenReturn(new ResultadoAutenticacion(true, "jane.smith", 106L, "Jane Smith",
                        List.of("web"), false, null, null));

        String basic = "Basic " + Base64.getEncoder()
                .encodeToString("web-client:banco-xyz-oauth-2026".getBytes(StandardCharsets.UTF_8));

        String token = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "canal_password")
                        .param("canal", "web")
                        .param("username", "jane.smith")
                        .param("password", "Banco2026*")
                        .param("scope", "web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = com.jayway.jsonpath.JsonPath.read(token, "$.access_token");
        var decoder = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var jwt = decoder.decode(accessToken);
        org.assertj.core.api.Assertions.assertThat(jwt.getAudience()).contains("web");
        org.assertj.core.api.Assertions.assertThat(jwt.getSubject()).isEqualTo("jane.smith");
        org.assertj.core.api.Assertions.assertThat(((Number) jwt.getClaim("cuentaId")).longValue()).isEqualTo(106L);
        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("typ")).isEqualTo("ACCESO");
    }
}
