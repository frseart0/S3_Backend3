package com.bancoxyz.batch.cuentasanuales;

import com.bancoxyz.batch.common.exception.InvalidAmountException;
import com.bancoxyz.batch.common.exception.InvalidDateFormatException;
import com.bancoxyz.batch.common.util.DateParsingUtils;
import com.bancoxyz.batch.common.util.NumberParsingUtils;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Valida, corrige y normaliza cada movimiento anual de una cuenta.
 *
 * <p>Reglas de negocio (ver README para el detalle completo):</p>
 * <ul>
 *   <li>{@code monto} vacio/no numerico es irrecuperable: se lanza
 *   {@link InvalidAmountException} y el {@code BankSkipPolicy} lo convierte
 *   en un skip auditado.</li>
 *   <li>{@code descripcion} vacia se <b>corrige</b> con un valor por
 *   defecto (no se rechaza el registro).</li>
 *   <li>Para {@code retiro}/{@code compra} el monto se <b>normaliza</b> a
 *   signo negativo (salida de dinero) para que el saldo neto anual se pueda
 *   calcular como una simple suma.</li>
 *   <li>Un {@code deposito} con monto no positivo, o un {@code transaccion}
 *   fuera del catalogo conocido, se conservan pero se marcan como
 *   {@code ANOMALIA} (este Job compila el estado de cuenta para auditoria,
 *   por lo que ocultar el registro seria contraproducente).</li>
 * </ul>
 */
public class MovimientoItemProcessor implements ItemProcessor<MovimientoRaw, MovimientoAnual> {

    private static final Set<String> TIPOS_CONOCIDOS = Set.of("deposito", "retiro", "compra");
    private static final String DESCRIPCION_DEFECTO = "Sin descripcion";

    @Override
    public MovimientoAnual process(MovimientoRaw raw) {
        BigDecimal montoOriginal = NumberParsingUtils.parseDecimalOrNull(raw.getMonto());
        if (montoOriginal == null) {
            throw new InvalidAmountException("Monto vacio o no numerico para la cuenta " + raw.getCuentaId());
        }

        List<String> problemas = new ArrayList<>();
        List<String> correcciones = new ArrayList<>();

        LocalDate fecha = null;
        try {
            fecha = DateParsingUtils.parseFlexible(raw.getFecha());
        } catch (InvalidDateFormatException ex) {
            problemas.add(ex.getMessage());
        }

        String tipoNormalizado = raw.getTransaccion() == null ? null : raw.getTransaccion().trim().toLowerCase();
        boolean tipoConocido = TIPOS_CONOCIDOS.contains(tipoNormalizado);
        if (!tipoConocido) {
            problemas.add("Tipo de transaccion anual desconocido: '" + raw.getTransaccion() + "'");
        }

        BigDecimal montoFinal;
        if ("deposito".equals(tipoNormalizado)) {
            if (montoOriginal.compareTo(BigDecimal.ZERO) <= 0) {
                problemas.add("Monto no positivo para un deposito: " + montoOriginal);
            }
            montoFinal = montoOriginal;
        } else if ("retiro".equals(tipoNormalizado) || "compra".equals(tipoNormalizado)) {
            montoFinal = montoOriginal.abs().negate();
            if (montoOriginal.compareTo(BigDecimal.ZERO) > 0) {
                correcciones.add("Se normalizo el signo del monto de salida (" + tipoNormalizado + ") a negativo");
            }
        } else {
            montoFinal = montoOriginal;
        }

        String descripcionFinal = raw.getDescripcion();
        if (descripcionFinal == null || descripcionFinal.isBlank()) {
            descripcionFinal = DESCRIPCION_DEFECTO;
            correcciones.add("Descripcion vacia, se uso el valor por defecto");
        }

        String estado;
        String motivo;
        if (!problemas.isEmpty()) {
            estado = "ANOMALIA";
            motivo = String.join("; ", problemas);
        } else if (!correcciones.isEmpty()) {
            estado = "VALIDA_CORREGIDA";
            motivo = String.join("; ", correcciones);
        } else {
            estado = "VALIDA";
            motivo = null;
        }

        return new MovimientoAnual(
                NumberParsingUtils.parseLongOrNull(raw.getCuentaId()),
                fecha,
                tipoNormalizado,
                montoFinal,
                descripcionFinal,
                estado,
                motivo,
                Thread.currentThread().getName());
    }
}
