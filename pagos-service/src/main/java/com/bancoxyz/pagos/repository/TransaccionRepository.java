package com.bancoxyz.pagos.repository;

import com.bancoxyz.pagos.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    Optional<Transaccion> findByReferencia(String referencia);

    List<Transaccion> findByCuentaOrigenOrCuentaDestinoOrderByIdDesc(Long cuentaOrigen, Long cuentaDestino);
}
