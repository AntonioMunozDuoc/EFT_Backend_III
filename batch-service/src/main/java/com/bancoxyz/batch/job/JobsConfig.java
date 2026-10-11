package com.bancoxyz.batch.job;

import com.bancoxyz.batch.model.Modelos.EstadoCsv;
import com.bancoxyz.batch.model.Modelos.EstadoMovimiento;
import com.bancoxyz.batch.model.Modelos.InteresCalculado;
import com.bancoxyz.batch.model.Modelos.InteresCsv;
import com.bancoxyz.batch.model.Modelos.MovimientoCsv;
import com.bancoxyz.batch.model.Modelos.MovimientoDiario;
import com.bancoxyz.batch.processor.Procesadores.EstadoProcessor;
import com.bancoxyz.batch.processor.Procesadores.InteresProcessor;
import com.bancoxyz.batch.processor.Procesadores.MovimientoProcessor;
import com.bancoxyz.batch.processor.RegistroInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Los tres procesos batch migrados desde COBOL/Shell. Cada job = step de chunks
 * (leer -> procesar -> escribir) + step final tasklet (reporte / consolidacion).
 *
 * Tolerancia a fallos (identica en los tres):
 *  - SKIP  : registros invalidos del legacy (RegistroInvalidoException, FlatFileParseException)
 *            se omiten y se auditan; si superan batch.skip-limit el step FALLA (politica de finalizacion).
 *  - RETRY : fallos transitorios de BD (conexion caida, timeout) se reintentan con espera
 *            exponencial antes de fallar el step.
 *  - RESTART: el JobRepository es JDBC; un job FAILED se reanuda (ver BatchLauncherService).
 * Rendimiento: chunks de batch.chunk-size y, en jobs 1 y 3, procesamiento multihilo.
 */
@Configuration
public class JobsConfig {

    private static final Logger log = LoggerFactory.getLogger(JobsConfig.class);

    public static final String JOB_DIARIO = "reporteTransaccionesDiariasJob";
    public static final String JOB_INTERESES = "calculoInteresesMensualesJob";
    public static final String JOB_ESTADOS = "estadosCuentaAnualesJob";

    private static final String DATASET = "#{jobParameters['dataset']}";

    @Value("${batch.chunk-size:100}")
    private int chunkSize;

    @Value("${batch.skip-limit:700}")
    private int skipLimit;

    @Value("${batch.concurrencia:4}")
    private int concurrencia;

    @Value("${batch.reports-dir:./reports}")
    private String reportsDir;

    @Bean
    public JdbcTemplate batchJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private RetryPolicy retryTransitorio() {
        return RetryPolicy.builder()
                .maxRetries(3)
                .delay(Duration.ofMillis(200))
                .multiplier(2)
                .includes(TransientDataAccessException.class,
                        RecoverableDataAccessException.class,
                        QueryTimeoutException.class)
                .build();
    }

    private <T> SynchronizedItemStreamReader<T> lector(
            String nombre, String dataset, String archivo, String[] columnas, FieldSetMapper<T> mapper) {

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
        tokenizer.setNames(columnas);

        DefaultLineMapper<T> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(mapper);

        FlatFileItemReader<T> reader = new FlatFileItemReader<>(lineMapper);
        reader.setName(nombre);
        reader.setResource(new ClassPathResource("data/" + dataset + "/" + archivo));
        reader.setLinesToSkip(1);
        reader.setEncoding("UTF-8");
        reader.setSaveState(false);   // lectura compartida entre hilos: sin estado de posicion

        // FlatFileItemReader no es thread-safe: se serializa la lectura, el procesamiento es paralelo
        return new SynchronizedItemStreamReader<>(reader);
    }

    // ======================================================================================
    // JOB 1 - Reporte de transacciones diarias
    // ======================================================================================

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<MovimientoCsv> movimientosReader(
            @Value(DATASET) String dataset) {

        return lector("movimientosReader", dataset, "movimientos_financieros_diarios.csv",
                new String[]{"id", "fecha", "monto", "tipo"},
                fs -> new MovimientoCsv(fs.readString(0), fs.readString(1), fs.readString(2), fs.readString(3)));
    }

    @Bean
    @StepScope
    public MovimientoProcessor movimientoProcessor(@Value(DATASET) String dataset) {
        return new MovimientoProcessor(dataset);
    }

    @Bean
    @StepScope
    public ItemWriter<MovimientoDiario> movimientoWriter(JdbcTemplate jdbc) {
        return chunk -> {
            List<MovimientoDiario> items = new ArrayList<>(chunk.getItems());
            jdbc.batchUpdate(
                    "INSERT INTO movimientos_diarios (id, fecha, monto, tipo, anomalia, dataset) "
                            + "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE fecha=VALUES(fecha), "
                            + "monto=VALUES(monto), tipo=VALUES(tipo), anomalia=VALUES(anomalia), "
                            + "dataset=VALUES(dataset)",
                    items, items.size(),
                    (ps, m) -> {
                        ps.setLong(1, m.id());
                        ps.setObject(2, m.fecha());
                        ps.setBigDecimal(3, m.monto());
                        ps.setString(4, m.tipo());
                        ps.setString(5, m.anomalia());
                        ps.setString(6, m.dataset());
                    });
        };
    }

    @Bean
    @StepScope
    public RechazosListener<MovimientoCsv, MovimientoDiario> rechazosDiario(
            JdbcTemplate jdbc, @Value(DATASET) String dataset) {
        return new RechazosListener<>(jdbc, JOB_DIARIO, dataset);
    }

    @Bean
    public Step cargarMovimientosStep(
            JobRepository jobRepository,
            PlatformTransactionManager tm,
            SynchronizedItemStreamReader<MovimientoCsv> movimientosReader,
            MovimientoProcessor movimientoProcessor,
            ItemWriter<MovimientoDiario> movimientoWriter,
            RechazosListener<MovimientoCsv, MovimientoDiario> rechazosDiario) {

        return new StepBuilder("cargarMovimientosStep", jobRepository)
                .<MovimientoCsv, MovimientoDiario>chunk(chunkSize)
                .reader(movimientosReader)
                .processor(movimientoProcessor)
                .writer(movimientoWriter)
                .transactionManager(tm)
                .faultTolerant()
                .skip(RegistroInvalidoException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(skipLimit)
                .retryPolicy(retryTransitorio())
                .skipListener(rechazosDiario)
                .taskExecutor(new SimpleAsyncTaskExecutor("batch-diario-") {{ setConcurrencyLimit(concurrencia); }})
                .build();
    }

    @Bean
    @StepScope
    public Tasklet resumenDiarioTasklet(JdbcTemplate jdbc, @Value(DATASET) String dataset) {
        return (contribution, chunkContext) -> {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT fecha, "
                            + "SUM(CASE WHEN tipo='debito' THEN monto ELSE 0 END) AS total_debitos, "
                            + "SUM(CASE WHEN tipo='credito' THEN monto ELSE 0 END) AS total_creditos, "
                            + "COUNT(*) AS cantidad, "
                            + "SUM(CASE WHEN anomalia IS NOT NULL THEN 1 ELSE 0 END) AS anomalias "
                            + "FROM movimientos_diarios WHERE dataset = ? GROUP BY fecha ORDER BY fecha", dataset);

            escribirCsv("reporte_transacciones_diarias_" + dataset + ".csv",
                    "fecha,total_debitos,total_creditos,cantidad,anomalias", filas,
                    List.of("fecha", "total_debitos", "total_creditos", "cantidad", "anomalias"));

            log.info("[{}] resumen diario generado: {} dias", JOB_DIARIO, filas.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step resumenDiarioStep(JobRepository jobRepository, PlatformTransactionManager tm,
                                  Tasklet resumenDiarioTasklet) {
        return new StepBuilder("resumenDiarioStep", jobRepository)
                .tasklet(resumenDiarioTasklet, tm)
                .build();
    }

    @Bean
    public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
                                              Step cargarMovimientosStep, Step resumenDiarioStep) {
        return new JobBuilder(JOB_DIARIO, jobRepository)
                .start(cargarMovimientosStep)
                .next(resumenDiarioStep)
                .build();
    }

    // ======================================================================================
    // JOB 2 - Calculo de intereses mensuales
    // ======================================================================================

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<InteresCsv> interesesReader(@Value(DATASET) String dataset) {
        return lector("interesesReader", dataset, "intereses_trimestrales.csv",
                new String[]{"cuenta_id", "nombre", "saldo", "edad", "tipo"},
                fs -> new InteresCsv(fs.readString(0), fs.readString(1), fs.readString(2),
                        fs.readString(3), fs.readString(4)));
    }

    @Bean
    @StepScope
    public InteresProcessor interesProcessor(@Value(DATASET) String dataset) {
        return new InteresProcessor(dataset, YearMonth.now().toString());
    }

    @Bean
    @StepScope
    public ItemWriter<InteresCalculado> interesWriter(JdbcTemplate jdbc) {
        return chunk -> {
            List<InteresCalculado> items = new ArrayList<>(chunk.getItems());
            jdbc.batchUpdate(
                    "INSERT INTO intereses_calculados (cuenta_id, nombre, tipo, saldo, edad, tasa_anual, "
                            + "interes_mensual, periodo, dataset) VALUES (?,?,?,?,?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), tipo=VALUES(tipo), saldo=VALUES(saldo), "
                            + "edad=VALUES(edad), tasa_anual=VALUES(tasa_anual), "
                            + "interes_mensual=VALUES(interes_mensual), periodo=VALUES(periodo), dataset=VALUES(dataset)",
                    items, items.size(),
                    (ps, i) -> {
                        ps.setLong(1, i.cuentaId());
                        ps.setString(2, i.nombre());
                        ps.setString(3, i.tipo());
                        ps.setBigDecimal(4, i.saldo());
                        ps.setObject(5, i.edad());
                        ps.setBigDecimal(6, i.tasaAnual());
                        ps.setBigDecimal(7, i.interesMensual());
                        ps.setString(8, i.periodo());
                        ps.setString(9, i.dataset());
                    });
        };
    }

    @Bean
    @StepScope
    public RechazosListener<InteresCsv, InteresCalculado> rechazosIntereses(
            JdbcTemplate jdbc, @Value(DATASET) String dataset) {
        return new RechazosListener<>(jdbc, JOB_INTERESES, dataset);
    }

    @Bean
    public Step calcularInteresesStep(
            JobRepository jobRepository,
            PlatformTransactionManager tm,
            SynchronizedItemStreamReader<InteresCsv> interesesReader,
            InteresProcessor interesProcessor,
            ItemWriter<InteresCalculado> interesWriter,
            RechazosListener<InteresCsv, InteresCalculado> rechazosIntereses) {

        // Un solo hilo: "se conserva el primer registro valido de cada cuenta" exige orden.
        return new StepBuilder("calcularInteresesStep", jobRepository)
                .<InteresCsv, InteresCalculado>chunk(chunkSize)
                .reader(interesesReader)
                .processor(interesProcessor)
                .writer(interesWriter)
                .transactionManager(tm)
                .faultTolerant()
                .skip(RegistroInvalidoException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(skipLimit)
                .retryPolicy(retryTransitorio())
                .skipListener(rechazosIntereses)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportarInteresesTasklet(JdbcTemplate jdbc, @Value(DATASET) String dataset) {
        return (contribution, chunkContext) -> {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT cuenta_id, nombre, tipo, saldo, tasa_anual, interes_mensual, periodo "
                            + "FROM intereses_calculados WHERE dataset = ? ORDER BY cuenta_id", dataset);

            escribirCsv("intereses_mensuales_" + dataset + ".csv",
                    "cuenta_id,nombre,tipo,saldo,tasa_anual,interes_mensual,periodo", filas,
                    List.of("cuenta_id", "nombre", "tipo", "saldo", "tasa_anual", "interes_mensual", "periodo"));

            log.info("[{}] intereses exportados: {} cuentas", JOB_INTERESES, filas.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step exportarInteresesStep(JobRepository jobRepository, PlatformTransactionManager tm,
                                      Tasklet exportarInteresesTasklet) {
        return new StepBuilder("exportarInteresesStep", jobRepository)
                .tasklet(exportarInteresesTasklet, tm)
                .build();
    }

    @Bean
    public Job calculoInteresesMensualesJob(JobRepository jobRepository,
                                            Step calcularInteresesStep, Step exportarInteresesStep) {
        return new JobBuilder(JOB_INTERESES, jobRepository)
                .start(calcularInteresesStep)
                .next(exportarInteresesStep)
                .build();
    }

    // ======================================================================================
    // JOB 3 - Estados de cuenta anuales
    // ======================================================================================

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<EstadoCsv> estadosReader(@Value(DATASET) String dataset) {
        return lector("estadosReader", dataset, "estados_financieros_anuales.csv",
                new String[]{"cuenta_id", "fecha", "transaccion", "monto", "descripcion"},
                fs -> new EstadoCsv(fs.readString(0), fs.readString(1), fs.readString(2),
                        fs.readString(3), fs.readString(4)));
    }

    @Bean
    @StepScope
    public EstadoProcessor estadoProcessor() {
        return new EstadoProcessor();
    }

    @Bean
    @StepScope
    public ItemWriter<EstadoMovimiento> estadoWriter(JdbcTemplate jdbc) {
        return chunk -> {
            List<EstadoMovimiento> items = new ArrayList<>(chunk.getItems());
            jdbc.batchUpdate(
                    "INSERT IGNORE INTO estado_cuenta_movimientos "
                            + "(clave, cuenta_id, fecha, transaccion, monto, descripcion) VALUES (?,?,?,?,?,?)",
                    items, items.size(),
                    (ps, e) -> {
                        ps.setString(1, e.clave());
                        ps.setLong(2, e.cuentaId());
                        ps.setObject(3, e.fecha());
                        ps.setString(4, e.transaccion());
                        ps.setBigDecimal(5, e.monto());
                        ps.setString(6, e.descripcion());
                    });
        };
    }

    @Bean
    @StepScope
    public RechazosListener<EstadoCsv, EstadoMovimiento> rechazosEstados(
            JdbcTemplate jdbc, @Value(DATASET) String dataset) {
        return new RechazosListener<>(jdbc, JOB_ESTADOS, dataset);
    }

    @Bean
    public Step cargarEstadosStep(
            JobRepository jobRepository,
            PlatformTransactionManager tm,
            SynchronizedItemStreamReader<EstadoCsv> estadosReader,
            EstadoProcessor estadoProcessor,
            ItemWriter<EstadoMovimiento> estadoWriter,
            RechazosListener<EstadoCsv, EstadoMovimiento> rechazosEstados) {

        return new StepBuilder("cargarEstadosStep", jobRepository)
                .<EstadoCsv, EstadoMovimiento>chunk(chunkSize)
                .reader(estadosReader)
                .processor(estadoProcessor)
                .writer(estadoWriter)
                .transactionManager(tm)
                .faultTolerant()
                .skip(RegistroInvalidoException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(skipLimit)
                .retryPolicy(retryTransitorio())
                .skipListener(rechazosEstados)
                .taskExecutor(new SimpleAsyncTaskExecutor("batch-estados-") {{ setConcurrencyLimit(concurrencia); }})
                .build();
    }

    @Bean
    public Tasklet consolidarEstadosTasklet(JdbcTemplate jdbc) {
        return (contribution, chunkContext) -> {
            jdbc.update(
                    "INSERT INTO estados_cuenta_anuales "
                            + "(cuenta_id, anio, total_depositos, total_egresos, saldo_neto, cantidad_movimientos) "
                            + "SELECT cuenta_id, YEAR(fecha), "
                            + "SUM(CASE WHEN monto > 0 THEN monto ELSE 0 END), "
                            + "SUM(CASE WHEN monto < 0 THEN -monto ELSE 0 END), "
                            + "SUM(monto), COUNT(*) "
                            + "FROM estado_cuenta_movimientos GROUP BY cuenta_id, YEAR(fecha) "
                            + "ON DUPLICATE KEY UPDATE total_depositos=VALUES(total_depositos), "
                            + "total_egresos=VALUES(total_egresos), saldo_neto=VALUES(saldo_neto), "
                            + "cantidad_movimientos=VALUES(cantidad_movimientos)");

            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT cuenta_id, anio, total_depositos, total_egresos, saldo_neto, cantidad_movimientos "
                            + "FROM estados_cuenta_anuales ORDER BY cuenta_id, anio");

            escribirCsv("estados_cuenta_anuales.csv",
                    "cuenta_id,anio,total_depositos,total_egresos,saldo_neto,cantidad_movimientos", filas,
                    List.of("cuenta_id", "anio", "total_depositos", "total_egresos", "saldo_neto",
                            "cantidad_movimientos"));

            log.info("[{}] estados de cuenta consolidados: {} filas", JOB_ESTADOS, filas.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step consolidarEstadosStep(JobRepository jobRepository, PlatformTransactionManager tm,
                                      Tasklet consolidarEstadosTasklet) {
        return new StepBuilder("consolidarEstadosStep", jobRepository)
                .tasklet(consolidarEstadosTasklet, tm)
                .build();
    }

    @Bean
    public Job estadosCuentaAnualesJob(JobRepository jobRepository,
                                       Step cargarEstadosStep, Step consolidarEstadosStep) {
        return new JobBuilder(JOB_ESTADOS, jobRepository)
                .start(cargarEstadosStep)
                .next(consolidarEstadosStep)
                .build();
    }

    // ------------------------------------------------------------------------ utilidades

    private void escribirCsv(String archivo, String encabezado,
                             List<Map<String, Object>> filas, List<String> columnas) throws IOException {

        Path dir = Path.of(reportsDir);
        Files.createDirectories(dir);

        List<String> lineas = new ArrayList<>();
        lineas.add(encabezado);
        for (Map<String, Object> fila : filas) {
            lineas.add(columnas.stream()
                    .map(c -> String.valueOf(fila.get(c)).replace(",", " "))
                    .collect(java.util.stream.Collectors.joining(",")));
        }

        Files.write(dir.resolve(archivo), lineas, StandardCharsets.UTF_8);
    }
}
