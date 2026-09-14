package com.bancoxyz.bff.common.seguridad;

/**
 * Un refresh token no sirve para llamar a los endpoints de negocio y un access
 * token no sirve para pedir un refresco; el claim {@code typ} lo distingue.
 */
public enum TipoToken {

    ACCESO,
    REFRESCO
}
