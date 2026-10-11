package com.bancoxyz.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

/** Audita en la tabla registros_rechazados cada registro omitido (skip) por calidad de datos. */
public class RechazosListener<I, O> implements SkipListener<I, O> {

    private static final Logger log = LoggerFactory.getLogger(RechazosListener.class);

    private final JdbcTemplate jdbc;
    private final String job;
    private final String dataset;

    public RechazosListener(JdbcTemplate jdbc, String job, String dataset) {
        this.jdbc = jdbc;
        this.job = job;
        this.dataset = dataset;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        registrar("(linea no parseable)", t);
    }

    @Override
    public void onSkipInProcess(I item, Throwable t) {
        registrar(String.valueOf(item), t);
    }

    @Override
    public void onSkipInWrite(O item, Throwable t) {
        registrar(String.valueOf(item), t);
    }

    private void registrar(String registro, Throwable t) {
        String motivo = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        log.debug("[{}] registro omitido: {} -> {}", job, registro, motivo);
        jdbc.update("INSERT INTO registros_rechazados (job, dataset, registro, motivo) VALUES (?,?,?,?)",
                job, dataset,
                registro.length() > 500 ? registro.substring(0, 500) : registro,
                motivo.length() > 255 ? motivo.substring(0, 255) : motivo);
    }
}
