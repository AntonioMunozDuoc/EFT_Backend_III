package com.bancoxyz.cuentas.repository;

import com.bancoxyz.cuentas.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId);

    Optional<Movimiento> findByOperationId(String operationId);
}
