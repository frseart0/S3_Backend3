package com.bancoxyz.core.demo;

import com.bancoxyz.core.autenticacion.DispositivoRepository;
import com.bancoxyz.core.autenticacion.UsuarioCanalRepository;
import com.bancoxyz.core.config.DemoProperties;
import com.bancoxyz.core.cuentas.CuentaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crea las identidades de prueba de los tres canales sobre cuentas que
 * realmente existen en el dataset legacy. Es idempotente: si el usuario ya
 * existe no lo toca, de modo que reiniciar el core-api no borra bloqueos ni
 * cambios de PIN.
 *
 * <p>Los canales estan repartidos a proposito para poder demostrar la
 * autorizacion por canal: {@code charlie.green} no tiene tarjeta y por lo
 * tanto no puede operar en cajeros, y {@code steve.rogers} no esta habilitado
 * en el canal movil.
 */
@Component
public class SembradorDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorDemo.class);

    private static final List<UsuarioDemo> USUARIOS = List.of(
            new UsuarioDemo("jane.smith", "Jane Smith", 106, "web,mobile,atm", "4051000000000106", "1234"),
            new UsuarioDemo("charlie.green", "Charlie Green", 109, "web,mobile", null, null),
            new UsuarioDemo("steve.rogers", "Steve Rogers", 117, "web,atm", "4051000000000117", "4321"));

    private static final List<DispositivoDemo> DISPOSITIVOS = List.of(
            new DispositivoDemo("ATM-001", "llave-atm-001", "Sucursal Centro"),
            new DispositivoDemo("ATM-002", "llave-atm-002", "Mall Plaza Norte"));

    private final UsuarioCanalRepository usuarios;
    private final DispositivoRepository dispositivos;
    private final CuentaRepository cuentas;
    private final PasswordEncoder encoder;
    private final DemoProperties propiedades;

    public SembradorDemo(UsuarioCanalRepository usuarios,
                         DispositivoRepository dispositivos,
                         CuentaRepository cuentas,
                         PasswordEncoder encoder,
                         DemoProperties propiedades) {
        this.usuarios = usuarios;
        this.dispositivos = dispositivos;
        this.cuentas = cuentas;
        this.encoder = encoder;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!propiedades.sembrar()) {
            return;
        }

        for (DispositivoDemo dispositivo : DISPOSITIVOS) {
            if (!dispositivos.existe(dispositivo.id())) {
                dispositivos.crear(dispositivo.id(), encoder.encode(dispositivo.clave()), dispositivo.ubicacion());
                log.info("Cajero {} registrado ({})", dispositivo.id(), dispositivo.ubicacion());
            }
        }

        for (UsuarioDemo usuario : USUARIOS) {
            if (!usuarios.existe(usuario.usuario())) {
                usuarios.crear(
                        usuario.usuario(),
                        encoder.encode(propiedades.password()),
                        usuario.nombre(),
                        usuario.cuentaId(),
                        usuario.canales(),
                        usuario.tarjeta(),
                        usuario.pin() == null ? null : encoder.encode(usuario.pin()));
                log.info("Usuario demo {} creado sobre la cuenta {} para los canales [{}]",
                        usuario.usuario(), usuario.cuentaId(), usuario.canales());
            }
            avisarSiFaltanDatos(usuario);
        }
    }

    /**
     * El core-api expone lo que el batch dejo en la base: sin los Jobs
     * ejecutados, las consultas de saldo y movimientos responderian 404. Vale
     * la pena decirlo al arrancar en vez de dejar que el canal falle.
     */
    private void avisarSiFaltanDatos(UsuarioDemo usuario) {
        if (cuentas.buscarSaldo(usuario.cuentaId()).isEmpty()) {
            log.warn("La cuenta {} de {} no tiene saldo: ejecute primero los Jobs del modulo "
                            + "banco-xyz-batch para poblar los datos del banco",
                    usuario.cuentaId(), usuario.usuario());
        }
    }

    private record UsuarioDemo(String usuario,
                               String nombre,
                               long cuentaId,
                               String canales,
                               String tarjeta,
                               String pin) {
    }

    private record DispositivoDemo(String id, String clave, String ubicacion) {
    }
}
