package com.bancoxyz.bff.atm.seguridad;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens invalidados antes de expirar. En un cajero cerrar sesion no es un
 * gesto opcional: cuando el cliente retira su tarjeta, el token que quedo en
 * la maquina no debe servir para nada mas.
 *
 * <p>Se mantiene en memoria porque las sesiones duran minutos y el registro se
 * purga solo. Con varias instancias del BFF detras de un balanceador habria
 * que mover este registro a un almacen compartido.
 */
@Component
public class SesionesCerradas {

    private final Map<String, Instant> cerradas = new ConcurrentHashMap<>();

    public void cerrar(String token, Instant expiraEn) {
        purgar();
        cerradas.put(token, expiraEn);
    }

    public boolean estaCerrada(String token) {
        Instant expiracion = cerradas.get(token);
        if (expiracion == null) {
            return false;
        }
        if (expiracion.isBefore(Instant.now())) {
            cerradas.remove(token);
            return false;
        }
        return true;
    }

    private void purgar() {
        Instant ahora = Instant.now();
        cerradas.entrySet().removeIf(entrada -> entrada.getValue().isBefore(ahora));
    }
}
