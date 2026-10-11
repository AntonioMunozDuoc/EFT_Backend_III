-- Clientes base (derivados de los titulares de intereses_trimestrales.csv, repo fin_legacy_data).
-- Solo con SQL_INIT_MODE=always. INSERT IGNORE: reiniciar no duplica.
INSERT IGNORE INTO clientes (id, documento, nombre, email, telefono, edad, perfil, estado) VALUES
(1, '11111111-1', 'John Doe',      'john.doe@bancoxyz.cl',      '+56911111111', 30, 'BASICO',  'ACTIVO'),
(2, '22222222-2', 'Jane Smith',    'jane.smith@bancoxyz.cl',    '+56922222222', 25, 'BASICO',  'ACTIVO'),
(3, '33333333-3', 'Bob Johnson',   'bob.johnson@bancoxyz.cl',   '+56933333333', 30, 'BASICO',  'ACTIVO'),
(4, '44444444-4', 'Alice Brown',   'alice.brown@bancoxyz.cl',   '+56944444444', 45, 'PREMIUM', 'ACTIVO'),
(5, '55555555-5', 'Charlie Green', 'charlie.green@bancoxyz.cl', '+56955555555', 35, 'PREMIUM', 'ACTIVO'),
(6, '66666666-6', 'Diana Prince',  'diana.prince@bancoxyz.cl',  '+56966666666', 40, 'PREMIUM', 'ACTIVO'),
(7, '77777777-7', 'Steve Rogers',  'steve.rogers@bancoxyz.cl',  '+56977777777', 80, 'SENIOR',  'ACTIVO');
