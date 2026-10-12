package com.bancoxyz.clientes.autenticacion;

import com.bancoxyz.clientes.error.OperacionRechazadaException;
import com.bancoxyz.clientes.error.RecursoNoEncontradoException;
import com.bancoxyz.domain.contract.PerfilCliente;
import com.bancoxyz.domain.contract.SolicitudActualizacionCliente;
import com.bancoxyz.domain.contract.SolicitudAltaCliente;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class ClienteService {

    private final UsuarioCanalRepository usuarios;
    private final PasswordEncoder encoder;

    public ClienteService(UsuarioCanalRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    public PerfilCliente alta(SolicitudAltaCliente solicitud) {
        if (solicitud.usuario() == null || solicitud.usuario().isBlank()) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        if (solicitud.password() == null || solicitud.password().isBlank()) {
            throw new IllegalArgumentException("La contrasena es obligatoria");
        }
        if (solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (solicitud.cuentaId() == null) {
            throw new IllegalArgumentException("La cuenta del cliente es obligatoria");
        }
        if (usuarios.existe(solicitud.usuario())) {
            throw new OperacionRechazadaException("CLIENTE_EXISTENTE",
                    "Ya existe un cliente con el usuario " + solicitud.usuario());
        }
        String canales = solicitud.canales() == null || solicitud.canales().isBlank()
                ? "web" : solicitud.canales();
        usuarios.crear(solicitud.usuario(), encoder.encode(solicitud.password()),
                solicitud.nombre(), solicitud.cuentaId(), canales, null, null);
        return perfil(solicitud.usuario());
    }

    public PerfilCliente perfil(String usuario) {
        RegistroUsuario registro = usuarios.buscarPorUsuario(usuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + usuario));
        return new PerfilCliente(
                registro.usuario(),
                registro.nombre(),
                registro.cuentaId(),
                new ArrayList<>(registro.canales()),
                registro.bloqueado());
    }

    public PerfilCliente actualizar(String usuario, SolicitudActualizacionCliente solicitud) {
        if (!usuarios.existe(usuario)) {
            throw new RecursoNoEncontradoException("No existe el cliente " + usuario);
        }
        usuarios.actualizar(usuario, solicitud.nombre(), solicitud.canales(), solicitud.bloqueado());
        return perfil(usuario);
    }
}
