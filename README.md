# Banco XYZ - Migración de Procesos Batch con Spring Batch

Proyecto de la asignatura **Desarrollo Backend III** (Semanas 1-3). Migra y moderniza 3 procesos batch legacy del "Banco XYZ" usando **Spring Batch**, leyendo los archivos CSV del dataset de referencia [KariVillagran/bank_legacy_data](https://github.com/KariVillagran/bank_legacy_data), validando/corrigiendo los datos y persistiéndolos en PostgreSQL.

## Objetivo del proyecto

Reescribir 3 procesos legacy del banco como Jobs de Spring Batch, cumpliendo con:

1. **Configuración de un proyecto Spring Batch** con Jobs y Steps para leer, procesar y escribir CSV.
2. **Procesamiento de datos**: lectura de CSV, transformación/validación con `ItemProcessor`, escritura en PostgreSQL.
3. **Manejo de errores y excepciones**: reglas de consistencia para datos incorrectos/mal clasificados.
4. **Políticas personalizadas de tolerancia a fallos**: `SkipPolicy` custom + retry con backoff.
5. **Escalamiento vía Partitioning**: cada Job particiona su archivo de origen y procesa las particiones en paralelo con un pool de hilos dedicado.

Los 3 Jobs implementados:


| Job                                     | Nombre (Spring Batch)            | Origen                | Descripción                                                                                              |
| --------------------------------------- | -------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------- |
| Reporte de Transacciones Diarias        | `reporteTransaccionesDiariasJob` | `transacciones.csv`   | Detecta anomalías (montos inválidos, tipos desconocidos, duplicados) y genera un resumen diario.         |
| Cálculo de Intereses Mensuales          | `calculoInteresesMensualesJob`   | `intereses.csv`       | Aplica la tasa de interés según el tipo de cuenta (ahorro/préstamo/hipoteca) y actualiza el saldo final. |
| Generación de Estados de Cuenta Anuales | `estadosCuentaAnualesJob`        | `cuentas_anuales.csv` | Compila los movimientos anuales por cuenta y genera el estado de cuenta para auditoría.                  |


## Arquitectura

```mermaid
flowchart TB
    subgraph job [Cada uno de los 3 Jobs]
        direction TB
        csv["Archivo CSV\n(classpath:data/*.csv)"] --> partitioner["LineRangePartitioner\n(gridSize particiones por rango de lineas)"]
        partitioner --> handler["TaskExecutorPartitionHandler\n(pool de N hilos)"]
        handler --> worker["Worker Step (por particion)\nreader -> processor -> writer"]
        worker --> db[("PostgreSQL")]
        worker -->|item saltado| errorlog[("batch_error_log")]
        db --> tasklet["Tasklet de agregacion\n(resumen / estado de cuenta)"]
    end
```



Cada Job sigue el mismo patrón:

1. **Step de partición (master)**: un `LineRangePartitioner` divide el archivo CSV en `gridSize` rangos de líneas de tamaño similar.
2. **Worker step (chunk-oriented,** `chunk=5`**)**: cada partición se procesa en paralelo (uno de los `poolSize` hilos del `TaskExecutorPartitionHandler`) con:
  - **Reader**: `FlatFileItemReader` step-scoped, acotado a su rango de líneas vía `setCurrentItemCount`/`setMaxItemCount`.
  - **Processor**: valida, corrige y clasifica cada fila (ver reglas de negocio abajo).
  - **Writer**: `JdbcBatchItemWriter` que inserta en la tabla de negocio correspondiente.
  - **Tolerancia a fallos**: `faultTolerant()` con un `SkipPolicy` custom (`BankSkipPolicy`) y una política de reintento con backoff exponencial para errores transitorios de BD.
3. **Tasklet de agregación** (Jobs de Transacciones y Cuentas Anuales): compila el resumen/estado de cuenta a partir de lo ya persistido.

Los datos originales del CSV **no se descartan silenciosamente**: cada fila termina en la base de datos con un `estado` (`VALIDA`, `VALIDA_CORREGIDA`, `ANOMALIA` o `DUPLICADA`) salvo que le falte un dato estructural imprescindible (ej. el monto), en cuyo caso el `ItemProcessor` lanza una excepción de validación que el `BankSkipPolicy` convierte en un "skip" registrado en `batch_error_log`.

## Estructura del código

```
src/main/java/com/bancoxyz/batch/
├── BatchApplication.java              Entry point (Spring Boot)
├── config/                            @ConfigurationProperties (partition, skip, retry, tasas de interes)
├── common/
│   ├── exception/                     Excepciones de validacion de negocio
│   ├── util/                          Parseo flexible de fechas/numeros
│   ├── partition/                     LineRangePartitioner + factory del PartitionHandler
│   ├── policy/                        BankSkipPolicy (tolerancia a fallos personalizada)
│   └── listener/                      BankSkipListener (auditoria) y JobSummaryListener (resumen en consola)
├── transacciones/                     Job 1: reader/processor/writer + Tasklet de resumen diario
├── intereses/                         Job 2: reader/processor/writer (calculo de interes)
└── cuentasanuales/                    Job 3: reader/processor/writer + Tasklet de estado de cuenta anual

src/main/resources/
├── application.yml                    Datasource, tasas, parametros de partitioning/skip/retry
├── schema-postgresql.sql              Tablas de negocio + batch_error_log
└── data/                               transacciones.csv, intereses.csv, cuentas_anuales.csv (dataset semana_3)
```

## Reglas de validación y corrección por dataset

### `transacciones.csv` (Job 1)

- Fecha: se prueban los formatos `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy`, `yyyy/MM/dd` (el legacy mezcla los 4). Si ninguno coincide, la transacción se marca `ANOMALIA`.
- `monto` vacío, no numérico, negativo o cero -> `ANOMALIA`.
- `tipo` fuera de `{debito, credito}` (ej. `invalid`, `desconocido`) -> `ANOMALIA`.
- IDs repetidos dentro de la misma ejecución -> `DUPLICADA` (se conservan para el resumen, no se descartan).
- El Tasklet `ResumenDiarioTasklet` agrega, por fecha: total, válidas, anómalas, duplicadas y montos totales por tipo.

### `intereses.csv` (Job 2)

- `tipo` fuera de `{ahorro, prestamo, hipoteca}` (ej. `-1`) -> **rechazo controlado**: se lanza `InvalidCategoryException`, que el `BankSkipPolicy` transforma en un skip auditado.
- `saldo` vacío/no numérico/negativo -> **rechazo controlado** (`InvalidAmountException`, skip).
- `edad` vacía o fuera de `[18, 90]` (configurable) -> **se corrige** acotando al límite más cercano (o usando la edad mínima si viene vacía) y el registro se marca `VALIDA_CORREGIDA`, documentando la corrección en `observacion`.
- Duplicados exactos (misma cuenta/nombre/saldo/edad/tipo) -> se **filtran** (no se insertan).
- Interés = `saldo_inicial * tasa` (tasa según tipo, configurable en `application.yml`); `saldo_final = saldo_inicial + interes`.

### `cuentas_anuales.csv` (Job 3)

- `monto` vacío/no numérico -> **rechazo controlado** (`InvalidAmountException`, skip): sin monto no se puede registrar el movimiento.
- `descripcion` vacía -> **se corrige** con el valor por defecto `"Sin descripcion"` (no se rechaza el registro).
- Para `retiro`/`compra` el monto se **normaliza** a signo negativo (salida de dinero), documentando la corrección si el signo original era positivo.
- Un `deposito` con monto no positivo, o un `transaccion` fuera de `{deposito, retiro, compra}`, se **conservan** pero se marcan `ANOMALIA` (el objetivo del Job es auditar, no ocultar información).
- El Tasklet `EstadoCuentaTasklet` agrega, por cuenta y año: total depósitos, total salidas, saldo neto anual, cantidad de movimientos y cantidad de anomalías.

## Tolerancia a fallos y escalamiento (Semanas 2 y 3)

- `BankSkipPolicy`: solo permite saltar excepciones de validación de datos conocidas (o errores de parseo de línea); cualquier otra excepción aborta el Job. Límite configurable vía `batch.skip.limit` (default 50 por partición).
- **Retry con backoff exponencial**: errores transitorios de escritura en BD (`TransientDataAccessException`) se reintentan hasta `batch.retry.limit` veces (default 3), con backoff configurable (`batch.retry.initial-interval-ms`, `multiplier`, `max-interval-ms`).
- `BankSkipListener`: registra cada item salteado en `batch_error_log` (job, step, etapa, detalle del item, mensaje de error) para trazabilidad/auditoría.
- **Escalamiento vía Partitioning** (decisión de la Semana 3, en vez de un simple step multi-hilo): `LineRangePartitioner` divide cada CSV en `batch.partition.grid-size` particiones (default **4**) de tamaño similar, que se procesan en paralelo con un `TaskExecutorPartitionHandler` sobre un pool de `batch.partition.pool-size` hilos (default **4**). El tamaño de chunk se mantiene en **5** (heredado de la Semana 2). Estos valores están centralizados en `application.yml` y se pueden ajustar sin tocar código.
- Cada fila persistida guarda en `procesado_por_hilo` el nombre del hilo del pool que la procesó, lo que permite verificar visualmente (con una simple consulta SQL) que el procesamiento efectivamente se paralelizó.

## Requisitos previos

- JDK 21+
- Docker y Docker Compose

## Cómo ejecutar

### 1. Levantar PostgreSQL

```bash
docker compose up -d
```

### 2. Compilar el proyecto

```powershell
.\mvnw.cmd clean package -DskipTests
```

### 3. Ejecutar cada Job de forma independiente

```powershell
# Job 1: Reporte de Transacciones Diarias
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.name=reporteTransaccionesDiariasJob"

# Job 2: Calculo de Intereses Mensuales
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.name=calculoInteresesMensualesJob"

# Job 3: Generacion de Estados de Cuenta Anuales
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.name=estadosCuentaAnualesJob"
```

### 4. Consultar los resultados

```sql
-- Transacciones diarias
SELECT estado, COUNT(*) FROM transacciones GROUP BY estado;
SELECT * FROM resumen_transacciones_diarias ORDER BY fecha;

-- Intereses
SELECT estado, COUNT(*) FROM cuentas_interes GROUP BY estado;
SELECT * FROM cuentas_interes ORDER BY cuenta_id LIMIT 20;

-- Estados de cuenta anuales
SELECT estado, COUNT(*) FROM movimientos_anuales GROUP BY estado;
SELECT * FROM estado_cuenta_anual ORDER BY cuenta_id, anio;

-- Evidencia de paralelismo (Partitioning)
SELECT procesado_por_hilo, COUNT(*) FROM transacciones GROUP BY procesado_por_hilo;

-- Errores / items saltados (tolerancia a fallos)
SELECT * FROM batch_error_log ORDER BY ocurrido_en DESC;
```

## Configuración relevante (`application.yml`)


| Propiedad                                                         | Default            | Descripción                                                    |
| ----------------------------------------------------------------- | ------------------ | -------------------------------------------------------------- |
| `batch.partition.grid-size`                                       | 4                  | Cantidad de particiones por archivo                            |
| `batch.partition.pool-size`                                       | 4                  | Hilos del pool que procesan las particiones en paralelo        |
| `batch.skip.limit`                                                | 50                 | Máximo de items saltados por partición antes de abortar el Job |
| `batch.retry.limit`                                               | 3                  | Reintentos ante errores transitorios de escritura              |
| `batch.intereses.tasa-ahorro` / `tasa-prestamo` / `tasa-hipoteca` | 0.5% / 1.5% / 1.0% | Tasas de interés mensual por tipo de cuenta                    |
| `batch.intereses.edad-minima` / `edad-maxima`                     | 18 / 90            | Rango de edad aceptado (fuera de rango se corrige)             |


