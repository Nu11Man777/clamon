CREATE DATABASE clamon_usuarios CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE clamon_negocio  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'app_usuarios'@'%' IDENTIFIED BY 'local-usuarios';
GRANT ALL ON clamon_usuarios.* TO 'app_usuarios'@'%';

CREATE USER 'app_negocio'@'%' IDENTIFIED BY 'local-negocio';
GRANT ALL ON clamon_negocio.* TO 'app_negocio'@'%';
