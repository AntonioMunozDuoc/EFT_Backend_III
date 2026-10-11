package com.bancoxyz.cuentas.repository;

import com.bancoxyz.cuentas.entity.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByClienteId(Long clienteId);

    /** Bloqueo pesimista: serializa debitos/creditos concurrentes sobre la misma cuenta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.cuentaId = :id")
    Optional<Cuenta> buscarParaActualizar(@Param("id") Long id);
}
