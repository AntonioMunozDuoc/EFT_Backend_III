-- Database-per-service: cada microservicio es dueno exclusivo de su esquema.
CREATE DATABASE IF NOT EXISTS banco_clientes CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS banco_cuentas  CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS banco_pagos    CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS banco_batch    CHARACTER SET utf8mb4;
