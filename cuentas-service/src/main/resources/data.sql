-- Datos base del microservicio de cuentas (repo fin_legacy_data, semana_1).
-- Solo se ejecuta con SQL_INIT_MODE=always (docker-compose). INSERT IGNORE lo hace repetible:
-- reiniciar el servicio no duplica ni pisa los saldos existentes.
-- cliente_id referencia (logicamente) a clientes-service: no hay FK entre bases.

INSERT IGNORE INTO cuentas (cuenta_id, cliente_id, nombre, edad, tipo, estado, saldo_actual, tasa_interes, interes_calculado) VALUES
(101, 1, 'John Doe',      30, 'ahorro',    'ACTIVA',  5000.00,  0.0150, 0.00),
(102, 2, 'Jane Smith',    25, 'prestamo',  'ACTIVA',  8000.00,  0.0500, 0.00),
(103, 3, 'Bob Johnson',   30, 'prestamo',  'ACTIVA', 12000.00,  0.0500, 0.00),
(104, 4, 'Alice Brown',   45, 'ahorro',    'ACTIVA',     0.00,  0.0150, 0.00),
(105, 5, 'Charlie Green', 35, 'hipoteca',  'ACTIVA',  7000.00,  0.0400, 0.00),
(106, 1, 'John Doe',      30, 'ahorro',    'ACTIVA',  5000.00,  0.0150, 0.00),
(107, 6, 'Diana Prince',  40, 'prestamo',  'ACTIVA', 15000.00,  0.0500, 0.00),
(108, 7, 'Steve Rogers',  80, 'ahorro',    'ACTIVA', 10000.00,  0.0150, 0.00);

INSERT IGNORE INTO movimientos (id, cuenta_id, fecha, tipo_movimiento, monto, descripcion) VALUES
(1, 101, '2024-01-01', 'deposito', 1000.00, 'Ingreso mensual'),
(2, 101, '2024-03-15', 'retiro',   -500.00, 'Retiro parcial'),
(3, 102, '2024-05-22', 'deposito', 1500.00, 'Ingreso mensual'),
(4, 103, '2024-07-10', 'deposito', 2000.00, 'Ingreso mensual'),
(5, 104, '2024-09-05', 'compra',   -100.00, 'Compra en tienda'),
(6, 105, '2024-10-01', 'deposito', 2500.00, 'Ingreso extra'),
(7, 106, '2024-11-20', 'deposito', 3000.00, 'Ingreso mensual'),
(8, 107, '2024-12-25', 'deposito',    0.00, 'Ingreso navideño'),
(9, 108, '2024-12-31', 'deposito', 2000.00, 'Ingreso de fin de año');
