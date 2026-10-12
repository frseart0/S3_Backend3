package com.bancoxyz.clientes.autenticacion;

import com.bancoxyz.domain.contract.PerfilCliente;
import com.bancoxyz.domain.contract.SolicitudActualizacionCliente;
import com.bancoxyz.domain.contract.SolicitudAltaCliente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/clientes")
@Tag(name = "Clientes", description = "Alta, consulta y mantenimiento de perfiles")
public class ClienteController {

    private final ClienteService clientes;

    public ClienteController(ClienteService clientes) {
        this.clientes = clientes;
    }

    @PostMapping
    @Operation(summary = "Da de alta un cliente y lo asocia a una cuenta")
    public PerfilCliente alta(@RequestBody SolicitudAltaCliente solicitud) {
        return clientes.alta(solicitud);
    }

    @GetMapping("/{usuario}")
    @Operation(summary = "Perfil personal del cliente")
    public PerfilCliente perfil(@PathVariable String usuario) {
        return clientes.perfil(usuario);
    }

    @PutMapping("/{usuario}")
    @Operation(summary = "Actualiza nombre, canales habilitados o el estado de bloqueo")
    public PerfilCliente actualizar(@PathVariable String usuario,
                                    @RequestBody SolicitudActualizacionCliente solicitud) {
        return clientes.actualizar(usuario, solicitud);
    }
}
