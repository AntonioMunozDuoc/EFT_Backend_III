package com.bancoxyz.batch.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Reglas de calidad de datos compartidas por los tres jobs. */
public final class Validaciones {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DMY_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DMY_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Validaciones() {
    }

    /** Acepta los 4 formatos presentes en el legacy: yyyy-MM-dd, yyyy/MM/dd, dd-MM-yyyy, dd/MM/yyyy. */
    public static LocalDate fecha(String texto) {

        String t = texto == null ? "" : texto.trim();

        try {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(t, ISO);
            if (t.matches("\\d{4}/\\d{2}/\\d{2}")) return LocalDate.parse(t, ISO_SLASH);
            if (t.matches("\\d{2}-\\d{2}-\\d{4}")) return LocalDate.parse(t, DMY_DASH);
            if (t.matches("\\d{2}/\\d{2}/\\d{4}")) return LocalDate.parse(t, DMY_SLASH);
        } catch (DateTimeParseException ex) {
            // cae al rechazo de abajo
        }

        throw new RegistroInvalidoException("Fecha invalida: '" + t + "'");
    }

    public static BigDecimal monto(String texto, String campo) {

        String t = texto == null ? "" : texto.trim();

        if (t.isEmpty()) {
            throw new RegistroInvalidoException(campo + " vacio");
        }

        try {
            return new BigDecimal(t);
        } catch (NumberFormatException ex) {
            throw new RegistroInvalidoException(campo + " no numerico: '" + t + "'");
        }
    }

    public static long entero(String texto, String campo) {

        String t = texto == null ? "" : texto.trim();

        try {
            return Long.parseLong(t);
        } catch (NumberFormatException ex) {
            throw new RegistroInvalidoException(campo + " invalido: '" + t + "'");
        }
    }

    public static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
