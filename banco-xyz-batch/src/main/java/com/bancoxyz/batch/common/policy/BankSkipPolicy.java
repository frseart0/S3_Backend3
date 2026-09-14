package com.bancoxyz.batch.common.policy;

import com.bancoxyz.batch.common.exception.DuplicateRecordException;
import com.bancoxyz.batch.common.exception.InvalidAmountException;
import com.bancoxyz.batch.common.exception.InvalidCategoryException;
import com.bancoxyz.batch.common.exception.InvalidDateFormatException;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;

import java.util.Set;

/**
 * Politica de tolerancia a fallos personalizada (requisito de la Semana 2).
 *
 * <p>Solo se permite "saltar" (skip) items cuya excepcion sea una de las
 * excepciones de validacion de datos conocidas, o un error de parseo de la
 * linea del archivo plano. Cualquier otra excepcion (ej. fallas de
 * infraestructura) NO es skippable y se propaga, marcando el Job/Step como
 * fallido. Ademas, se respeta un limite maximo de items salteados por
 * step/partition para evitar enmascarar un problema masivo de calidad de
 * datos.</p>
 */
public class BankSkipPolicy implements SkipPolicy {

    @SuppressWarnings("unchecked")
    private static final Set<Class<? extends Throwable>> SKIPPABLE_EXCEPTIONS = Set.of(
            FlatFileParseException.class,
            InvalidDateFormatException.class,
            InvalidAmountException.class,
            InvalidCategoryException.class,
            DuplicateRecordException.class,
            NumberFormatException.class
    );

    private final int skipLimit;

    public BankSkipPolicy(int skipLimit) {
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) throws SkipLimitExceededException {
        boolean isSkippable = SKIPPABLE_EXCEPTIONS.stream().anyMatch(type -> type.isInstance(throwable));
        if (!isSkippable) {
            return false;
        }
        if (skipCount >= skipLimit) {
            throw new SkipLimitExceededException(skipLimit, throwable);
        }
        return true;
    }
}
