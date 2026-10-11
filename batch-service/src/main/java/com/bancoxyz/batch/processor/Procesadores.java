package com.bancoxyz.batch.processor;

import com.bancoxyz.batch.model.Modelos.EstadoCsv;
import com.bancoxyz.batch.model.Modelos.EstadoMovimiento;
import com.bancoxyz.batch.model.Modelos.InteresCalculado;
import com.bancoxyz.batch.model.Modelos.InteresCsv;
import com.bancoxyz.batch.model.Modelos.MovimientoCsv;
import com.bancoxyz.batch.model.Modelos.MovimientoDiario;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Procesadores (validacion + transformacion) de los tres jobs. */
public final class Procesadores {

    private Procesadores() {
    }

    // ----------------------------------------------------------- Job 1: transacciones diarias

    /**
     * Reglas del reporte diario:
     *  - fecha en cualquiera de los 4 formatos; tipo debito|credito; monto numerico -> si no, se rechaza.
     *  - ANOMALIA (se conserva y se marca): MONTO_NEGATIVO, MONTO_CERO.
     */
    public static class MovimientoProcessor implements ItemProcessor<MovimientoCsv, MovimientoDiario> {

        private final String dataset;

        public MovimientoProcessor(String dataset) {
            this.dataset = dataset;
        }

        @Override
        public MovimientoDiario process(MovimientoCsv in) {

            long id = Validaciones.entero(in.id(), "id");
            LocalDate fecha = Validaciones.fecha(in.fecha());
            BigDecimal monto = Validaciones.monto(in.monto(), "monto");
            String tipo = Validaciones.limpio(in.tipo()).toLowerCase();

            if (!tipo.equals("debito") && !tipo.equals("credito")) {
                throw new RegistroInvalidoException("Tipo de transaccion no valido: '" + tipo + "'");
            }

            String anomalia = null;
            if (monto.signum() < 0) {
                anomalia = "MONTO_NEGATIVO";
            } else if (monto.signum() == 0) {
                anomalia = "MONTO_CERO";
            }

            return new MovimientoDiario(id, fecha, monto, tipo, anomalia, dataset);
        }
    }

    // ----------------------------------------------------------- Job 2: intereses mensuales

    /**
     * Reglas del calculo de intereses:
     *  - tipo ahorro|prestamo|hipoteca; saldo obligatorio y >= 0; edad (si viene) entre 18 y 110.
     *  - duplicados: se conserva el PRIMER registro valido de cada cuenta (los demas se filtran).
     *  - interes mensual = saldo * tasa anual / 12 (HALF_UP, 2 decimales).
     *
     * Este step corre en un solo hilo a proposito: "el primero gana" necesita orden determinista.
     */
    public static class InteresProcessor implements ItemProcessor<InteresCsv, InteresCalculado> {

        static final Map<String, BigDecimal> TASAS = Map.of(
                "ahorro", new BigDecimal("0.0150"),
                "prestamo", new BigDecimal("0.0500"),
                "hipoteca", new BigDecimal("0.0400"));

        private final String dataset;
        private final String periodo;
        private final Set<Long> vistas = ConcurrentHashMap.newKeySet();

        public InteresProcessor(String dataset, String periodo) {
            this.dataset = dataset;
            this.periodo = periodo;
        }

        @Override
        public InteresCalculado process(InteresCsv in) {

            long cuentaId = Validaciones.entero(in.cuentaId(), "cuenta_id");
            String tipo = Validaciones.limpio(in.tipo()).toLowerCase();

            BigDecimal tasa = TASAS.get(tipo);
            if (tasa == null) {
                throw new RegistroInvalidoException("Tipo de cuenta no valido: '" + tipo + "'");
            }

            BigDecimal saldo = Validaciones.monto(in.saldo(), "saldo");
            if (saldo.signum() < 0) {
                throw new RegistroInvalidoException("Saldo negativo");
            }

            Integer edad = null;
            if (!Validaciones.limpio(in.edad()).isEmpty()) {
                long e = Validaciones.entero(in.edad(), "edad");
                if (e < 18 || e > 110) {
                    throw new RegistroInvalidoException("Edad fuera de rango: " + e);
                }
                edad = (int) e;
            }

            if (!vistas.add(cuentaId)) {
                return null;   // duplicado: filtrado (no es error)
            }

            BigDecimal interes = saldo.multiply(tasa)
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            return new InteresCalculado(cuentaId, Validaciones.limpio(in.nombre()), tipo, saldo,
                    edad, tasa, interes, periodo, dataset);
        }
    }

    // ----------------------------------------------------------- Job 3: estados de cuenta anuales

    /**
     * Reglas del estado de cuenta:
     *  - transaccion: deposito|depósito|retiro|compra|pago (se normaliza el acento).
     *  - monto numerico y distinto de cero; el signo se normaliza por tipo
     *    (deposito = positivo; retiro/compra/pago = negativo).
     *  - descripcion vacia -> "Sin descripcion".
     *  - "clave" (MD5 de los campos) evita duplicar filas identicas al reejecutar.
     */
    public static class EstadoProcessor implements ItemProcessor<EstadoCsv, EstadoMovimiento> {

        @Override
        public EstadoMovimiento process(EstadoCsv in) {

            long cuentaId = Validaciones.entero(in.cuentaId(), "cuenta_id");
            LocalDate fecha = Validaciones.fecha(in.fecha());

            String tx = Validaciones.limpio(in.transaccion()).toLowerCase().replace("ó", "o");
            if (!Set.of("deposito", "retiro", "compra", "pago").contains(tx)) {
                throw new RegistroInvalidoException("Transaccion no valida: '" + tx + "'");
            }

            BigDecimal monto = Validaciones.monto(in.monto(), "monto");
            if (monto.signum() == 0) {
                throw new RegistroInvalidoException("Monto cero");
            }

            BigDecimal normalizado = tx.equals("deposito") ? monto.abs() : monto.abs().negate();

            String desc = Validaciones.limpio(in.descripcion());
            if (desc.isEmpty()) {
                desc = "Sin descripcion";
            }

            String clave = md5(cuentaId + "|" + fecha + "|" + tx + "|" + normalizado.toPlainString() + "|" + desc);

            return new EstadoMovimiento(clave, cuentaId, fecha, tx, normalizado, desc);
        }

        private static String md5(String texto) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("MD5").digest(texto.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
