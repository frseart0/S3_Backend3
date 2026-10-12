package com.bancoxyz.cuentas.cuentas;

import com.bancoxyz.cuentas.error.RecursoNoEncontradoException;
import com.bancoxyz.domain.contract.DetalleInteres;
import com.bancoxyz.domain.contract.EstadoCuentaAnual;
import com.bancoxyz.domain.contract.Movimiento;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.PerfilCuenta;
import com.bancoxyz.domain.contract.SaldoCuenta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Lecturas de una cuenta. El dinero lo mueve el servicio de pagos.
 */
@RestController
@RequestMapping("/internal/cuentas/{cuentaId}")
@Tag(name = "Cuentas", description = "Datos migrados por el batch")
public class CuentaController {

    private static final int TAMANO_MAXIMO_PAGINA = 200;

    private final CuentaRepository cuentas;

    public CuentaController(CuentaRepository cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/perfil")
    @Operation(summary = "Perfil vigente del titular segun el ultimo registro confiable del batch")
    public PerfilCuenta perfil(@PathVariable long cuentaId) {
        return cuentas.buscarPerfil(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay un perfil confiable para la cuenta " + cuentaId));
    }

    @GetMapping("/saldo")
    @Operation(summary = "Saldo vigente de la cuenta")
    public SaldoCuenta saldo(@PathVariable long cuentaId) {
        return cuentas.buscarSaldo(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta " + cuentaId + " no tiene saldo registrado"));
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Movimientos paginados de la cuenta, del mas reciente al mas antiguo")
    public PaginaMovimientos movimientos(
            @PathVariable long cuentaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        if (pagina < 0) {
            throw new IllegalArgumentException("El numero de pagina no puede ser negativo");
        }
        if (tamano <= 0 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw new IllegalArgumentException("El tamano de pagina debe estar entre 1 y " + TAMANO_MAXIMO_PAGINA);
        }
        exigirCuentaExistente(cuentaId);

        List<Movimiento> contenido = cuentas.movimientos(cuentaId, desde, hasta, pagina, tamano);
        long total = cuentas.contarMovimientos(cuentaId, desde, hasta);
        return new PaginaMovimientos(contenido, pagina, tamano, total);
    }

    @GetMapping("/intereses")
    @Operation(summary = "Resultado del calculo de intereses mensuales (Job 2)")
    public DetalleInteres intereses(@PathVariable long cuentaId) {
        return cuentas.buscarIntereses(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta " + cuentaId + " no tiene calculo de intereses confiable"));
    }

    @GetMapping("/estados-anuales")
    @Operation(summary = "Estados de cuenta anuales agregados por el batch (Job 3)")
    public List<EstadoCuentaAnual> estadosAnuales(@PathVariable long cuentaId) {
        exigirCuentaExistente(cuentaId);
        return cuentas.estadosAnuales(cuentaId);
    }

    private void exigirCuentaExistente(long cuentaId) {
        if (!cuentas.existe(cuentaId)) {
            throw new RecursoNoEncontradoException("La cuenta " + cuentaId + " no existe");
        }
    }
}
