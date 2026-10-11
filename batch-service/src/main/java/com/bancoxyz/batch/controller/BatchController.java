package com.bancoxyz.batch.controller;

import com.bancoxyz.batch.job.BatchLauncherService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private static final Set<String> DATASETS = Set.of("semana_1", "semana_3");

    private final BatchLauncherService launcher;
    private final JdbcTemplate jdbc;

    public BatchController(BatchLauncherService launcher, JdbcTemplate jdbc) {
        this.launcher = launcher;
        this.jdbc = jdbc;
    }

    @GetMapping("/jobs")
    public Set<String> jobs() {
        return launcher.nombresDeJobs();
    }

    /** Ejecuta el job y responde cuando termina (los volumenes del ejercicio toman segundos). */
    @PostMapping("/jobs/{nombre}/ejecuciones")
    public Map<String, Object> ejecutar(
            @PathVariable String nombre,
            @RequestParam(defaultValue = "semana_3") String dataset) throws Exception {

        if (!DATASETS.contains(dataset)) {
            throw new IllegalArgumentException("Dataset invalido. Use: " + DATASETS);
        }
        if (!launcher.nombresDeJobs().contains(nombre)) {
            throw new IllegalArgumentException("Job desconocido. Use: " + launcher.nombresDeJobs());
        }
        return launcher.ejecutar(nombre, dataset);
    }

    /** Registros rechazados por calidad de datos (auditoria del skip). */
    @GetMapping("/rechazos")
    public List<Map<String, Object>> rechazos(
            @RequestParam(required = false) String job,
            @RequestParam(defaultValue = "50") int limite) {

        int max = Math.min(Math.max(limite, 1), 500);
        return job == null
                ? jdbc.queryForList("SELECT job, dataset, registro, motivo FROM registros_rechazados ORDER BY id DESC LIMIT ?", max)
                : jdbc.queryForList("SELECT job, dataset, registro, motivo FROM registros_rechazados WHERE job = ? ORDER BY id DESC LIMIT ?", job, max);
    }

    /** Conteos de resultado para comparar contra el legacy. */
    @GetMapping("/resultados")
    public Map<String, Object> resultados() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("movimientos_diarios", jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_diarios", Long.class));
        r.put("movimientos_con_anomalia", jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_diarios WHERE anomalia IS NOT NULL", Long.class));
        r.put("intereses_calculados", jdbc.queryForObject("SELECT COUNT(*) FROM intereses_calculados", Long.class));
        r.put("estado_cuenta_movimientos", jdbc.queryForObject("SELECT COUNT(*) FROM estado_cuenta_movimientos", Long.class));
        r.put("estados_cuenta_anuales", jdbc.queryForObject("SELECT COUNT(*) FROM estados_cuenta_anuales", Long.class));
        r.put("registros_rechazados", jdbc.queryForObject("SELECT COUNT(*) FROM registros_rechazados", Long.class));
        return r;
    }
}
