package com.bancoxyz.pagos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de una transaccion de pagos. "referencia" (unica) es la clave de
 * idempotencia que entrega el cliente: repetir la misma referencia devuelve la
 * transaccion original en lugar de cobrar dos veces.
 */
@Entity
@Table(name = "transacciones")
public class Transaccion {

    public static final String PAGO = "PAGO";
    public static final String TRANSFERENCIA = "TRANSFERENCIA";
    public static final String DEPOSITO = "DEPOSITO";
    public static final String RETIRO = "RETIRO";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String FALLIDA = "FALLIDA";
    public static final String COMPENSADA = "COMPENSADA";
    public static final String REVISION_MANUAL = "REVISION_MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "cuenta_origen")
    private Long cuentaOrigen;

    @Column(name = "cuenta_destino")
    private Long cuentaDestino;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false, unique = true, length = 80)
    private String referencia;

    @Column(name = "detalle_error", length = 255)
    private String detalleError;

    @Column(name = "saldo_resultante", precision = 15, scale = 2)
    private BigDecimal saldoResultante;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public Transaccion() {
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Long getCuentaOrigen() { return cuentaOrigen; }
    public void setCuentaOrigen(Long cuentaOrigen) { this.cuentaOrigen = cuentaOrigen; }
    public Long getCuentaDestino() { return cuentaDestino; }
    public void setCuentaDestino(Long cuentaDestino) { this.cuentaDestino = cuentaDestino; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getDetalleError() { return detalleError; }
    public void setDetalleError(String detalleError) { this.detalleError = detalleError; }
    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public void setSaldoResultante(BigDecimal saldoResultante) { this.saldoResultante = saldoResultante; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
