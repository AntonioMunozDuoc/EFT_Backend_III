package com.bancoxyz.clientes.service;

import com.bancoxyz.clientes.dto.ClienteRequest;
import com.bancoxyz.clientes.dto.ClienteResponse;
import com.bancoxyz.clientes.entity.Cliente;
import com.bancoxyz.clientes.exception.ClienteDuplicadoException;
import com.bancoxyz.clientes.exception.RecursoNoEncontradoException;
import com.bancoxyz.clientes.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return repository.findAll().stream().map(this::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtener(Long id) {
        return aRespuesta(buscar(id));
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request) {

        if (repository.existsByDocumento(request.documento())) {
            throw new ClienteDuplicadoException("Ya existe un cliente con el documento " + request.documento());
        }
        if (repository.existsByEmail(request.email())) {
            throw new ClienteDuplicadoException("Ya existe un cliente con el email " + request.email());
        }

        Cliente cliente = new Cliente();
        aplicar(cliente, request);
        return aRespuesta(repository.save(cliente));
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest request) {

        Cliente cliente = buscar(id);

        if (repository.existsByDocumentoAndIdNot(request.documento(), id)) {
            throw new ClienteDuplicadoException("Ya existe otro cliente con el documento " + request.documento());
        }
        if (repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ClienteDuplicadoException("Ya existe otro cliente con el email " + request.email());
        }

        aplicar(cliente, request);
        return aRespuesta(repository.save(cliente));
    }

    /** Baja logica: el cliente se conserva (hay cuentas y movimientos que lo referencian). */
    @Transactional
    public ClienteResponse darDeBaja(Long id) {
        Cliente cliente = buscar(id);
        cliente.setEstado(Cliente.INACTIVO);
        return aRespuesta(repository.save(cliente));
    }

    private Cliente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado: " + id));
    }

    private void aplicar(Cliente cliente, ClienteRequest r) {
        cliente.setDocumento(r.documento().trim());
        cliente.setNombre(r.nombre().trim());
        cliente.setEmail(r.email().trim().toLowerCase());
        cliente.setTelefono(r.telefono());
        cliente.setEdad(r.edad());
        cliente.setPerfil(r.perfil() == null || r.perfil().isBlank() ? "BASICO" : r.perfil().toUpperCase());
    }

    private ClienteResponse aRespuesta(Cliente c) {
        return new ClienteResponse(c.getId(), c.getDocumento(), c.getNombre(), c.getEmail(),
                c.getTelefono(), c.getEdad(), c.getPerfil(), c.getEstado());
    }
}
