package com.bancoxyz.pagos.cuentas;

import com.bancoxyz.domain.contract.ComprobanteOperacion;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.SolicitudMovimiento;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import com.bancoxyz.domain.contract.SolicitudTransferencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/cuentas/{cuentaId}")
@Tag(name = "Pagos", description = "Retiros, depositos, transferencias y pagos")
public class PagoController {

    private final RetiroService retiros;
    private final OperacionService operaciones;

    public PagoController(RetiroService retiros, OperacionService operaciones) {
        this.retiros = retiros;
        this.operaciones = operaciones;
    }

    @PostMapping("/retiros")
    @Operation(summary = "Aplica un retiro de forma atomica e idempotente")
    public ComprobanteRetiro retirar(@PathVariable long cuentaId, @RequestBody SolicitudRetiro solicitud) {
        return retiros.retirar(cuentaId, solicitud);
    }

    @PostMapping("/depositos")
    @Operation(summary = "Acredita un deposito")
    public ComprobanteOperacion depositar(@PathVariable long cuentaId, @RequestBody SolicitudMovimiento solicitud) {
        return operaciones.depositar(cuentaId, solicitud);
    }

    @PostMapping("/pagos")
    @Operation(summary = "Descuenta un pago")
    public ComprobanteOperacion pagar(@PathVariable long cuentaId, @RequestBody SolicitudMovimiento solicitud) {
        return operaciones.pagar(cuentaId, solicitud);
    }

    @PostMapping("/transferencias")
    @Operation(summary = "Mueve saldo de esta cuenta a otra")
    public ComprobanteOperacion transferir(@PathVariable long cuentaId,
                                           @RequestBody SolicitudTransferencia solicitud) {
        return operaciones.transferir(cuentaId, solicitud);
    }
}
