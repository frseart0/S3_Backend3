package com.bancoxyz.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos de prueba para poder demostrar los tres canales sin un sistema de
 * onboarding. Se desactiva con {@code core.demo.sembrar=false}.
 */
@ConfigurationProperties(prefix = "core.demo")
public record DemoProperties(boolean sembrar, String password) {
}
