#!/usr/bin/env bash
# Prueba de humo de extremo a extremo (requiere: docker compose up -d --build).
# Uso: bash scripts/smoke-test.sh
set -u
AUTH=http://localhost:9000/oauth2/token
OK=0; FAIL=0

token() {  # token <client> <secret> <scope>
  curl -s -u "$1:$2" -d "grant_type=client_credentials&scope=$3" "$AUTH" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}
check() {  # check <descripcion> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then echo "  OK    $1 ($3)"; OK=$((OK+1)); else echo "  FALLA $1 (esperado $2, obtenido $3)"; FAIL=$((FAIL+1)); fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "== Seguridad OAuth2"
WEB=$(token web-app web-secret-dev web.access)
ATM=$(token atm-app atm-secret-dev atm.access)
BATCH=$(token batch-operator batch-operator-secret-dev batch.access)
check "BFF web sin token -> 401" 401 "$(code http://localhost:8081/api/web/cuentas)"
check "BFF web con scope web.access -> 200" 200 "$(code -H "Authorization: Bearer $WEB" http://localhost:8081/api/web/cuentas)"
check "BFF web con token de ATM -> 403" 403 "$(code -H "Authorization: Bearer $ATM" http://localhost:8081/api/web/cuentas)"

echo "== BFF ATM -> pagos-service -> cuentas-service"
check "Saldo cuenta 101" 200 "$(code -H "Authorization: Bearer $ATM" http://localhost:8083/api/atm/saldo/101)"
check "Retiro 100 en cuenta 101" 200 "$(code -H "Authorization: Bearer $ATM" -H 'Content-Type: application/json' -d '{"cuentaId":101,"monto":100}' http://localhost:8083/api/atm/retiros)"
check "Retiro sin saldo -> 409" 409 "$(code -H "Authorization: Bearer $ATM" -H 'Content-Type: application/json' -d '{"cuentaId":104,"monto":99999}' http://localhost:8083/api/atm/retiros)"

echo "== BFF Web: transferencia (saga) y compensacion"
check "Transferencia 101 -> 106" 200 "$(code -H "Authorization: Bearer $WEB" -H 'Content-Type: application/json' -d '{"cuentaOrigen":101,"cuentaDestino":106,"monto":50}' http://localhost:8081/api/web/transferencias)"
check "Transferencia a cuenta inexistente -> compensada (409)" 409 "$(code -H "Authorization: Bearer $WEB" -H 'Content-Type: application/json' -d '{"cuentaOrigen":101,"cuentaDestino":9999,"monto":10}' http://localhost:8081/api/web/transferencias)"

echo "== batch-service (Spring Batch)"
for J in reporteTransaccionesDiariasJob calculoInteresesMensualesJob estadosCuentaAnualesJob; do
  check "Job $J" 200 "$(code -X POST -H "Authorization: Bearer $BATCH" "http://localhost:8087/api/batch/jobs/$J/ejecuciones?dataset=semana_3")"
done

echo; echo "Resultado: $OK OK, $FAIL con fallas"
[ "$FAIL" -eq 0 ]
