package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.domain.Canal;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El aislamiento entre canales depende de estas validaciones: si el claim de
 * audiencia o el tipo de token no se revisaran, un token de un canal serviria
 * en otro.
 */
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-prueba-banco-xyz-con-mas-de-32-bytes";

    private final JwtService servicioWeb = servicioPara(Canal.WEB);
    private final JwtService servicioMovil = servicioPara(Canal.MOBILE);

    @Test
    void validaUnTokenEmitidoPorElMismoCanal() {
        var usuario = UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.WEB);

        UsuarioCanal validado = servicioWeb.validar(servicioWeb.emitirAcceso(usuario), TipoToken.ACCESO);

        assertThat(validado.usuario()).isEqualTo("jane.smith");
        assertThat(validado.cuentaId()).isEqualTo(106L);
        assertThat(validado.canal()).isEqualTo(Canal.WEB);
    }

    /**
     * Mismo secreto y mismo emisor: lo unico que cambia es la audiencia. Es el
     * caso que demuestra que el aislamiento no depende de tener secretos
     * distintos por canal.
     */
    @Test
    void rechazaUnTokenEmitidoParaOtroCanal() {
        String tokenMovil = servicioMovil.emitirAcceso(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.MOBILE));

        assertThatThrownBy(() -> servicioWeb.validar(tokenMovil, TipoToken.ACCESO))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no fue emitido para el canal web");
    }

    @Test
    void rechazaUnRefreshTokenUsadoComoAccessToken() {
        String refresco = servicioMovil.emitirRefresco(
                UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.MOBILE));

        assertThatThrownBy(() -> servicioMovil.validar(refresco, TipoToken.ACCESO))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("ACCESO");
    }

    @Test
    void rechazaUnTokenExpirado() throws InterruptedException {
        var propiedades = new JwtProperties(
                SECRETO, "banco-xyz-test", Canal.WEB, Duration.ofMillis(1), null);
        var servicio = new JwtService(propiedades);
        String token = servicio.emitirAcceso(UsuarioCanal.de("jane.smith", 106L, "Jane Smith", Canal.WEB));
        Thread.sleep(1100);

        assertThatThrownBy(() -> servicio.validar(token, TipoToken.ACCESO))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnaFirmaAjena() {
        var otroSecreto = new JwtProperties(
                "otro-secreto-distinto-de-prueba-con-32-bytes", "banco-xyz-test",
                Canal.WEB, Duration.ofMinutes(5), null);
        String tokenAjeno = new JwtService(otroSecreto).emitirAcceso(
                UsuarioCanal.de("atacante", 999L, "Atacante", Canal.WEB));

        assertThatThrownBy(() -> servicioWeb.validar(tokenAjeno, TipoToken.ACCESO))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void exigeUnSecretoSuficientementeLargo() {
        assertThatThrownBy(() -> new JwtProperties("corto", "banco-xyz-test",
                Canal.WEB, Duration.ofMinutes(5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    private static JwtService servicioPara(Canal canal) {
        return new JwtService(new JwtProperties(
                SECRETO, "banco-xyz-test", canal, Duration.ofMinutes(30), Duration.ofDays(30)));
    }
}
