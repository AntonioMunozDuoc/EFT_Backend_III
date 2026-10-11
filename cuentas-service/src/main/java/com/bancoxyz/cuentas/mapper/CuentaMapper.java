package com.bancoxyz.cuentas.mapper;

import com.bancoxyz.cuentas.dto.CuentaResponse;
import com.bancoxyz.cuentas.dto.MovimientoResponse;
import com.bancoxyz.cuentas.entity.Cuenta;
import com.bancoxyz.cuentas.entity.Movimiento;
import org.springframework.stereotype.Component;

@Component
public class CuentaMapper {

    public CuentaResponse toResponse(Cuenta c) {
        return new CuentaResponse(
                c.getCuentaId(), c.getClienteId(), c.getNombre(), c.getEdad(),
                c.getTipo(), c.getEstado(), c.getSaldoActual(),
                c.getTasaInteres(), c.getInteresCalculado());
    }

    public MovimientoResponse toResponse(Movimiento m) {
        return new MovimientoResponse(
                m.getId(), m.getCuentaId(), m.getFecha(),
                m.getTipoMovimiento(), m.getMonto(), m.getDescripcion());
    }
}
