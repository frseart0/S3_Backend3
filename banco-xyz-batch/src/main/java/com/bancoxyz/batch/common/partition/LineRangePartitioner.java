package com.bancoxyz.batch.common.partition;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Particiona un unico archivo CSV en {@code gridSize} rangos de lineas de
 * tamano similar, para que cada partition procese en paralelo un subconjunto
 * disjunto de filas del mismo archivo (estrategia de escalamiento via
 * Partitioning descrita en los requerimientos de la Semana 3).
 *
 * <p>Cada {@link ExecutionContext} generado contiene {@code startLine} y
 * {@code endLine} (1-indexados sobre las filas de datos, sin contar el
 * encabezado), que luego el {@code ItemReader} step-scoped de cada worker
 * step utiliza junto con {@code setCurrentItemCount}/{@code setMaxItemCount}
 * para leer unicamente su porcion del archivo.</p>
 */
public class LineRangePartitioner implements Partitioner {

    private static final String PARTITION_PREFIX = "partition";

    private final Resource resource;

    public LineRangePartitioner(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalDataLines = countDataLines(resource);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        if (totalDataLines <= 0) {
            ExecutionContext emptyContext = new ExecutionContext();
            emptyContext.putInt("startLine", 1);
            emptyContext.putInt("endLine", 0);
            emptyContext.putString("partitionName", PARTITION_PREFIX + 0);
            partitions.put(PARTITION_PREFIX + 0, emptyContext);
            return partitions;
        }

        int linesPerPartition = (int) Math.ceil((double) totalDataLines / gridSize);
        int start = 1;
        int partitionNumber = 0;
        while (start <= totalDataLines) {
            int end = Math.min(start + linesPerPartition - 1, totalDataLines);
            String partitionName = PARTITION_PREFIX + partitionNumber;

            ExecutionContext context = new ExecutionContext();
            context.putInt("startLine", start);
            context.putInt("endLine", end);
            context.putString("partitionName", partitionName);
            partitions.put(partitionName, context);

            start = end + 1;
            partitionNumber++;
        }
        return partitions;
    }

    private int countDataLines(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            long totalLines = reader.lines().count();
            return (int) Math.max(0, totalLines - 1);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo leer el archivo para calcular las particiones: " + resource.getFilename(), ex);
        }
    }
}
