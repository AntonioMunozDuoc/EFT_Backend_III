package com.bancoxyz.common.resilience;

/** Utilidad para recuperar la causa original dentro del fallback del Circuit Breaker. */
public final class Causas {

    private Causas() {
    }

    public static <T extends Throwable> T buscar(Throwable throwable, Class<T> tipo) {

        Throwable actual = throwable;

        while (actual != null) {

            if (tipo.isInstance(actual)) {
                return tipo.cast(actual);
            }

            actual = actual.getCause();
        }

        return null;
    }
}
