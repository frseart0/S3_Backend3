package com.bancoxyz.batch.transacciones;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una fila cruda de {@code transacciones.csv}, tal cual llega del
 * sistema legacy (todos los campos como String, ya que pueden venir vacios
 * o con formatos invalidos).
 */
@Data
@NoArgsConstructor
public class TransaccionRaw {

    private String id;
    private String fecha;
    private String monto;
    private String tipo;
}
