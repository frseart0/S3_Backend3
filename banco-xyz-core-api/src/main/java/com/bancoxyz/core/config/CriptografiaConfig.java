package com.bancoxyz.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El core-api guarda contrasenas, PIN y claves de dispositivo siempre con
 * hash: ninguna de las tres se puede recuperar desde la base de datos.
 */
@Configuration
public class CriptografiaConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
