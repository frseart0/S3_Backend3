package com.bancoxyz.batch.intereses;

import com.bancoxyz.batch.common.exception.InvalidAmountException;
import com.bancoxyz.batch.common.exception.InvalidCategoryException;
import com.bancoxyz.batch.common.util.NumberParsingUtils;
import com.bancoxyz.batch.config.InteresProperties;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valida, corrige y calcula el interes mensual de cada cuenta.
 *
 * <p>Reglas de negocio (ver README para el detalle completo):</p>
 * <ul>
 *   <li>Duplicados exactos (misma cuenta/nombre/saldo/edad/tipo) se
 *   <b>filtran</b> silenciosamente (no se insertan, {@code filterCount++}).</li>
 *   <li>{@code tipo} fuera de {@code {ahorro, prestamo, hipoteca}} o
 *   {@code saldo} vacio/negativo son errores irrecuperables: se lanza una
 *   excepcion de validacion que el {@code BankSkipPolicy} convierte en un
 *   skip registrado en {@code batch_error_log}.</li>
 *   <li>{@code edad} vacia o fuera del rango [edadMinima, edadMaxima] se
 *   <b>corrige</b> (se acota al limite mas cercano o se usa la edad minima
 *   como valor por defecto) y el registro se marca como
 *   {@code VALIDA_CORREGIDA} en vez de rechazarse.</li>
 * </ul>
 *
 * <p>Bean singleton (no step-scoped) para compartir el set de duplicados
 * entre todas las particiones de la misma ejecucion del Job.</p>
 */
public class InteresItemProcessor implements ItemProcessor<InteresRaw, CuentaInteres> {

    private static final Set<String> TIPOS_VALIDOS = Set.of("ahorro", "prestamo", "hipoteca");

    private final InteresProperties interesProperties;
    private final Set<String> clavesVistas = ConcurrentHashMap.newKeySet();

    public InteresItemProcessor(InteresProperties interesProperties) {
        this.interesProperties = interesProperties;
    }

    @Override
    public CuentaInteres process(InteresRaw raw) {
        String claveDuplicado = String.join("|",
                nullSafe(raw.getCuentaId()), nullSafe(raw.getNombre()),
                nullSafe(raw.getSaldo()), nullSafe(raw.getEdad()), nullSafe(raw.getTipo()));
        if (!clavesVistas.add(claveDuplicado)) {
            return null; // duplicado exacto -> filtrado (no se persiste)
        }

        String tipoNormalizado = raw.getTipo() == null ? null : raw.getTipo().trim().toLowerCase();
        if (!TIPOS_VALIDOS.contains(tipoNormalizado)) {
            throw new InvalidCategoryException("Tipo de cuenta invalido: '" + raw.getTipo() + "'");
        }

        BigDecimal saldoInicial = NumberParsingUtils.parseDecimalOrNull(raw.getSaldo());
        if (saldoInicial == null) {
            throw new InvalidAmountException("Saldo vacio o no numerico para la cuenta " + raw.getCuentaId());
        }
        if (saldoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Saldo negativo no permitido: " + saldoInicial);
        }

        Integer edadOriginal = NumberParsingUtils.parseIntOrNull(raw.getEdad());
        String observacion = null;
        int edadUtilizada;
        if (edadOriginal == null) {
            edadUtilizada = interesProperties.edadMinima();
            observacion = "Edad no informada, se utilizo la edad minima configurada (" + edadUtilizada + ")";
        } else if (edadOriginal < interesProperties.edadMinima()) {
            edadUtilizada = interesProperties.edadMinima();
            observacion = "Edad fuera de rango (" + edadOriginal + "), se acoto al minimo (" + edadUtilizada + ")";
        } else if (edadOriginal > interesProperties.edadMaxima()) {
            edadUtilizada = interesProperties.edadMaxima();
            observacion = "Edad fuera de rango (" + edadOriginal + "), se acoto al maximo (" + edadUtilizada + ")";
        } else {
            edadUtilizada = edadOriginal;
        }

        BigDecimal tasaAplicada = tasaParaTipo(tipoNormalizado);
        BigDecimal interesCalculado = saldoInicial.multiply(tasaAplicada).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoFinal = saldoInicial.add(interesCalculado).setScale(2, RoundingMode.HALF_UP);

        String estado = observacion == null ? "VALIDA" : "VALIDA_CORREGIDA";

        return new CuentaInteres(
                NumberParsingUtils.parseLongOrNull(raw.getCuentaId()),
                raw.getNombre(),
                tipoNormalizado,
                saldoInicial,
                edadOriginal,
                edadUtilizada,
                tasaAplicada,
                interesCalculado,
                saldoFinal,
                estado,
                observacion,
                Thread.currentThread().getName());
    }

    private BigDecimal tasaParaTipo(String tipoNormalizado) {
        return switch (tipoNormalizado) {
            case "ahorro" -> interesProperties.tasaAhorro();
            case "prestamo" -> interesProperties.tasaPrestamo();
            case "hipoteca" -> interesProperties.tasaHipoteca();
            default -> throw new InvalidCategoryException("Tipo de cuenta invalido: '" + tipoNormalizado + "'");
        };
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
