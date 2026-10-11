package com.bancoxyz.clientes.repository;

import com.bancoxyz.clientes.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByDocumento(String documento);

    boolean existsByEmail(String email);

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);
}
