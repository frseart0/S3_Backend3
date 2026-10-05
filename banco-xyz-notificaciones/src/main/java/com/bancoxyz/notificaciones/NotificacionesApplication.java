package com.bancoxyz.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Consumidor del topico {@code banco.retiros.realizados}. No mueve saldo:
 * deja constancia asincrona de cada retiro que el core ya confirmo.
 */
@SpringBootApplication
public class NotificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificacionesApplication.class, args);
    }
}
