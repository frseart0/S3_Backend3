package com.bancoxyz.core.autenticacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UsuarioCanalRepository {

    private static final String COLUMNAS = """
            SELECT id, usuario, password_hash, nombre, cuenta_id, canales_permitidos,
                   tarjeta, pin_hash, intentos_fallidos, bloqueado
            FROM usuarios_canal
            """;

    private final JdbcTemplate jdbc;

    public UsuarioCanalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RegistroUsuario> buscarPorUsuario(String usuario) {
        return jdbc.query(COLUMNAS + " WHERE usuario = ?", mapeador(), usuario).stream().findFirst();
    }

    public Optional<RegistroUsuario> buscarPorTarjeta(String tarjeta) {
        return jdbc.query(COLUMNAS + " WHERE tarjeta = ?", mapeador(), tarjeta).stream().findFirst();
    }

    /**
     * Suma un intento fallido y bloquea la identidad cuando se alcanza el
     * maximo permitido. Se hace en una sola sentencia para que dos intentos
     * simultaneos no se pisen el contador.
     */
    public int registrarIntentoFallido(long id, int maxIntentos) {
        jdbc.update("""
                UPDATE usuarios_canal
                SET intentos_fallidos = intentos_fallidos + 1,
                    bloqueado = (intentos_fallidos + 1) >= ?
                WHERE id = ?
                """, maxIntentos, id);
        Integer intentos = jdbc.queryForObject(
                "SELECT intentos_fallidos FROM usuarios_canal WHERE id = ?", Integer.class, id);
        return intentos == null ? 0 : intentos;
    }

    public void limpiarIntentos(long id) {
        jdbc.update("UPDATE usuarios_canal SET intentos_fallidos = 0 WHERE id = ? AND intentos_fallidos > 0", id);
    }

    public boolean existe(String usuario) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuarios_canal WHERE usuario = ?)", Boolean.class, usuario));
    }

    public void crear(String usuario,
                      String passwordHash,
                      String nombre,
                      long cuentaId,
                      String canalesPermitidos,
                      String tarjeta,
                      String pinHash) {
        jdbc.update("""
                INSERT INTO usuarios_canal
                    (usuario, password_hash, nombre, cuenta_id, canales_permitidos, tarjeta, pin_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, usuario, passwordHash, nombre, cuentaId, canalesPermitidos, tarjeta, pinHash);
    }

    private RowMapper<RegistroUsuario> mapeador() {
        return (rs, fila) -> new RegistroUsuario(
                rs.getLong("id"),
                rs.getString("usuario"),
                rs.getString("password_hash"),
                rs.getString("nombre"),
                rs.getLong("cuenta_id"),
                rs.getString("canales_permitidos"),
                rs.getString("tarjeta"),
                rs.getString("pin_hash"),
                rs.getInt("intentos_fallidos"),
                rs.getBoolean("bloqueado"));
    }
}
