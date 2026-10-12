package com.bancoxyz.bff.web.cliente;

import com.bancoxyz.bff.common.resiliencia.Espera;
import com.bancoxyz.bff.common.seguridad.UsuarioCanal;
import com.bancoxyz.bff.web.resiliencia.LlamadasNucleoWeb;
import com.bancoxyz.domain.contract.ComprobanteOperacion;
import com.bancoxyz.domain.contract.PerfilCliente;
import com.bancoxyz.domain.contract.SolicitudMovimiento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/web")
@Tag(name = "Cliente web")
public class ClienteWebController {

    private final LlamadasNucleoWeb nucleo;

    public ClienteWebController(LlamadasNucleoWeb nucleo) {
        this.nucleo = nucleo;
    }

    @GetMapping("/perfil-cliente")
    @Operation(summary = "Perfil personal del cliente autenticado")
    public PerfilCliente perfil(@AuthenticationPrincipal UsuarioCanal usuario) {
        return Espera.join(nucleo.perfilCliente(usuario.usuario()));
    }

    @PostMapping("/depositos")
    @Operation(summary = "Deposito en la cuenta del token")
    public ComprobanteOperacion depositar(@AuthenticationPrincipal UsuarioCanal usuario,
                                          @RequestBody SolicitudDeposito solicitud) {
        return Espera.join(nucleo.depositar(usuario.cuentaId(), new SolicitudMovimiento(
                solicitud.monto(), "web", solicitud.claveIdempotencia(), null)));
    }

    public record SolicitudDeposito(BigDecimal monto, String claveIdempotencia) {
    }
}
