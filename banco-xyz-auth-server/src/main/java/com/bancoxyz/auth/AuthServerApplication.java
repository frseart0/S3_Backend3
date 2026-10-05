package com.bancoxyz.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Authorization Server del banco. Los BFF no firman tokens: se los pide a
 * este servicio con el grant {@code canal_password}, y el nucleo sigue siendo
 * quien guarda el hash de la contrasena y del PIN.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
