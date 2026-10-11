package com.bancoxyz.pagos.client;

import java.math.BigDecimal;

record OperacionPayload(String operationId, String tipo, BigDecimal monto, String descripcion) {
}
