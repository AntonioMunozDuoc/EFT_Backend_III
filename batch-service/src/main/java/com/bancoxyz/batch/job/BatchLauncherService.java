package com.bancoxyz.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lanza los jobs y aplica la politica de REEJECUCION AUTOMATICA: si un job termina
 * FAILED (falla critica: BD caida mas alla de los reintentos, limite de skips superado...),
 * se REANUDA la misma instancia (restart) hasta batch.reejecucion.max-intentos veces,
 * esperando batch.reejecucion.espera-ms entre intentos. Como los writers son upserts,
 * reprocesar un chunk nunca duplica datos.
 */
@Service
public class BatchLauncherService {

    private static final Logger log = LoggerFactory.getLogger(BatchLauncherService.class);

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;
    private final int maxIntentos;
    private final long esperaMs;

    public BatchLauncherService(
            JobOperator jobOperator,
            Map<String, Job> jobs,
            @Value("${batch.reejecucion.max-intentos:3}") int maxIntentos,
            @Value("${batch.reejecucion.espera-ms:2000}") long esperaMs) {

        this.jobOperator = jobOperator;
        this.jobs = jobs;
        this.maxIntentos = maxIntentos;
        this.esperaMs = esperaMs;
    }

    public Set<String> nombresDeJobs() {
        return Set.of(JobsConfig.JOB_DIARIO, JobsConfig.JOB_INTERESES, JobsConfig.JOB_ESTADOS);
    }

    public Map<String, Object> ejecutar(String nombreJob, String dataset) throws Exception {

        Job job = jobs.get(nombreJob);
        if (job == null) {
            throw new IllegalArgumentException("Job desconocido: " + nombreJob);
        }

        JobParameters params = new JobParametersBuilder()
                .addString("dataset", dataset)
                .addLong("ejecutadoEn", System.currentTimeMillis())
                .toJobParameters();

        log.info("Lanzando job {} (dataset={})", nombreJob, dataset);
        long inicio = System.currentTimeMillis();

        JobExecution ejecucion = jobOperator.start(job, params);
        int intento = 1;

        while (ejecucion.getStatus() == BatchStatus.FAILED && intento < maxIntentos) {

            log.warn("Job {} FAILED (intento {}/{}): se reanuda en {} ms",
                    nombreJob, intento, maxIntentos, esperaMs);

            Thread.sleep(esperaMs);
            ejecucion = jobOperator.restart(ejecucion);
            intento++;
        }

        return resumen(ejecucion, nombreJob, dataset, intento, System.currentTimeMillis() - inicio);
    }

    private Map<String, Object> resumen(
            JobExecution e, String job, String dataset, int intentos, long duracionMs) {

        List<Map<String, Object>> pasos = new ArrayList<>();
        for (StepExecution s : e.getStepExecutions()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("step", s.getStepName());
            p.put("estado", s.getStatus().toString());
            p.put("leidos", s.getReadCount());
            p.put("escritos", s.getWriteCount());
            p.put("filtrados", s.getFilterCount());
            p.put("omitidos", s.getSkipCount());
            pasos.add(p);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("job", job);
        r.put("dataset", dataset);
        r.put("ejecucionId", e.getId());
        r.put("estado", e.getStatus().toString());
        r.put("exitCode", e.getExitStatus().getExitCode());
        r.put("intentos", intentos);
        r.put("duracionMs", duracionMs);
        r.put("steps", pasos);
        return r;
    }
}
