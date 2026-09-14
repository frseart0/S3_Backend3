package com.bancoxyz.batch.transacciones;

import com.bancoxyz.batch.common.exception.InvalidDateFormatException;
import com.bancoxyz.batch.common.util.DateParsingUtils;
import com.bancoxyz.batch.common.util.NumberParsingUtils;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valida y clasifica cada transaccion diaria.
 *
 * <p>A diferencia de un simple filtro, este Job debe "detectar anomalias y
 * generar un resumen": por eso las transacciones invalidas NO se descartan,
 * se conservan con {@code estado=ANOMALIA} y el motivo detectado, para que
 * el Tasklet de resumen las pueda contabilizar.</p>
 *
 * <p>Este bean es un singleton (no step-scoped) para que el set de ids
 * vistos se comparta entre todas las particiones de una misma ejecucion del
 * Job y asi se puedan detectar duplicados entre particiones distintas.</p>
 */
public class TransaccionItemProcessor implements ItemProcessor<TransaccionRaw, Transaccion> {

    private static final Set<String> TIPOS_VALIDOS = Set.of("debito", "credito");

    private final Set<String> idsVistos = ConcurrentHashMap.newKeySet();

    @Override
    public Transaccion process(TransaccionRaw raw) {
        String hilo = Thread.currentThread().getName();
        Long idOrigen = NumberParsingUtils.parseLongOrNull(raw.getId());
        String tipoNormalizado = normalizar(raw.getTipo());

        // Se parsean fecha/monto siempre (best-effort), incluso para
        // duplicados, para que el resumen diario pueda seguir agrupando por
        // fecha aunque el registro este marcado como DUPLICADA.
        List<String> problemas = new ArrayList<>();

        LocalDate fecha = null;
        try {
            fecha = DateParsingUtils.parseFlexible(raw.getFecha());
        } catch (InvalidDateFormatException ex) {
            problemas.add(ex.getMessage());
        }

        BigDecimal monto = NumberParsingUtils.parseDecimalOrNull(raw.getMonto());
        if (monto == null) {
            problemas.add("Monto vacio o no numerico");
        } else if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            problemas.add("Monto no positivo: " + monto);
        }

        if (!TIPOS_VALIDOS.contains(tipoNormalizado)) {
            problemas.add("Tipo de transaccion invalido: '" + raw.getTipo() + "'");
        }

        boolean esDuplicado = raw.getId() != null && !raw.getId().isBlank()
                && !idsVistos.add(raw.getId().trim());

        String estado;
        String motivo;
        if (esDuplicado) {
            estado = "DUPLICADA";
            motivo = "Id de transaccion duplicado: " + raw.getId();
        } else if (!problemas.isEmpty()) {
            estado = "ANOMALIA";
            motivo = String.join("; ", problemas);
        } else {
            estado = "VALIDA";
            motivo = null;
        }

        return new Transaccion(idOrigen, fecha, monto, tipoNormalizado, estado, motivo, hilo);
    }

    private String normalizar(String tipo) {
        return tipo == null ? null : tipo.trim().toLowerCase();
    }
}
