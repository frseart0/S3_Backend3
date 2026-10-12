package com.bancoxyz.cuentas.gestion;

import com.bancoxyz.cuentas.error.OperacionRechazadaException;
import com.bancoxyz.cuentas.error.RecursoNoEncontradoException;
import com.bancoxyz.domain.contract.CuentaOperativa;
import com.bancoxyz.domain.contract.SolicitudAperturaCuenta;
import com.bancoxyz.domain.contract.SolicitudMantenimientoCuenta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class CuentaGestionService {

    private static final Set<String> TIPOS = Set.of("ahorro", "prestamo", "hipoteca");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    public CuentaGestionService(JdbcTemplate jdbc, TransactionTemplate transacciones) {
        this.jdbc = jdbc;
        this.transacciones = transacciones;
    }

    public CuentaOperativa abrir(SolicitudAperturaCuenta solicitud) {
        String tipo = normalizarTipo(solicitud.tipo() == null ? "ahorro" : solicitud.tipo());
        BigDecimal saldo = solicitud.saldoInicial() == null ? BigDecimal.ZERO : solicitud.saldoInicial();
        if (saldo.signum() < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser negativo");
        }
        return transacciones.execute(estado -> {
            long id = solicitud.cuentaId() != null ? solicitud.cuentaId() : siguienteId();
            if (existeFila(id)) {
                throw new OperacionRechazadaException("CUENTA_EXISTENTE", "La cuenta " + id + " ya esta abierta");
            }
            jdbc.update("""
                    INSERT INTO cuenta (cuenta_id, tipo, estado, alias, cliente_usuario)
                    VALUES (?, ?, 'ABIERTA', ?, ?)
                    """, id, tipo, solicitud.alias(), solicitud.clienteUsuario());
            jdbc.update("""
                    INSERT INTO cuenta_saldo (cuenta_id, saldo)
                    VALUES (?, ?)
                    ON CONFLICT (cuenta_id) DO NOTHING
                    """, id, saldo);
            return exigir(id);
        });
    }

    public CuentaOperativa cerrar(long cuentaId) {
        return transacciones.execute(estado -> {
            Optional<CuentaOperativa> actual = buscar(cuentaId);
            if (actual.isPresent()) {
                if (!"CERRADA".equals(actual.get().estado())) {
                    jdbc.update("""
                            UPDATE cuenta
                            SET estado = 'CERRADA', cerrada_en = now(), actualizado_en = now()
                            WHERE cuenta_id = ?
                            """, cuentaId);
                }
                return exigir(cuentaId);
            }
            if (!tieneHuella(cuentaId)) {
                throw new RecursoNoEncontradoException("La cuenta " + cuentaId + " no existe");
            }
            jdbc.update("""
                    INSERT INTO cuenta (cuenta_id, tipo, estado, alias)
                    VALUES (?, 'ahorro', 'CERRADA', NULL)
                    """, cuentaId);
            jdbc.update("UPDATE cuenta SET cerrada_en = now() WHERE cuenta_id = ?", cuentaId);
            return exigir(cuentaId);
        });
    }

    public CuentaOperativa mantener(long cuentaId, SolicitudMantenimientoCuenta solicitud) {
        String tipo = solicitud.tipo() == null ? null : normalizarTipo(solicitud.tipo());
        return transacciones.execute(estado -> {
            if (!existeFila(cuentaId)) {
                if (!tieneHuella(cuentaId)) {
                    throw new RecursoNoEncontradoException("La cuenta " + cuentaId + " no existe");
                }
                jdbc.update("""
                        INSERT INTO cuenta (cuenta_id, tipo, estado, alias)
                        VALUES (?, ?, 'ABIERTA', ?)
                        """, cuentaId, tipo == null ? "ahorro" : tipo, solicitud.alias());
                return exigir(cuentaId);
            }
            jdbc.update("""
                    UPDATE cuenta
                    SET alias = COALESCE(?, alias),
                        tipo = COALESCE(?, tipo),
                        actualizado_en = now()
                    WHERE cuenta_id = ?
                    """, solicitud.alias(), tipo, cuentaId);
            return exigir(cuentaId);
        });
    }

    public CuentaOperativa consultar(long cuentaId) {
        return buscar(cuentaId).orElseThrow(() -> new RecursoNoEncontradoException(
                "La cuenta " + cuentaId + " no tiene ficha de ciclo de vida"));
    }

    private CuentaOperativa exigir(long cuentaId) {
        return buscar(cuentaId).orElseThrow(() -> new RecursoNoEncontradoException(
                "La cuenta " + cuentaId + " no quedo registrada"));
    }

    private Optional<CuentaOperativa> buscar(long cuentaId) {
        String sql = """
                SELECT cuenta_id, tipo, estado, alias, cliente_usuario
                FROM cuenta WHERE cuenta_id = ?
                """;
        return jdbc.query(sql, (rs, fila) -> new CuentaOperativa(
                rs.getLong("cuenta_id"),
                rs.getString("tipo"),
                rs.getString("estado"),
                rs.getString("alias"),
                rs.getString("cliente_usuario")), cuentaId).stream().findFirst();
    }

    private boolean existeFila(long cuentaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cuenta WHERE cuenta_id = ?)", Boolean.class, cuentaId));
    }

    private boolean tieneHuella(long cuentaId) {
        String sql = """
                SELECT EXISTS (SELECT 1 FROM cuenta_saldo WHERE cuenta_id = ?)
                    OR EXISTS (SELECT 1 FROM cuentas_interes WHERE cuenta_id = ?)
                    OR EXISTS (SELECT 1 FROM movimientos_anuales WHERE cuenta_id = ?)
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, cuentaId, cuentaId, cuentaId));
    }

    private long siguienteId() {
        Long maximo = jdbc.queryForObject("""
                SELECT GREATEST(
                    COALESCE((SELECT MAX(cuenta_id) FROM cuenta), 0),
                    COALESCE((SELECT MAX(cuenta_id) FROM cuenta_saldo), 0)
                ) + 1
                """, Long.class);
        return maximo == null ? 1L : maximo;
    }

    private static String normalizarTipo(String tipo) {
        String normalizado = tipo.trim().toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(normalizado)) {
            throw new IllegalArgumentException("El tipo de cuenta debe ser ahorro, prestamo o hipoteca");
        }
        return normalizado;
    }
}
