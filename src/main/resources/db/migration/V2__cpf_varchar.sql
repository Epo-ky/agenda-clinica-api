-- CHAR(11) vira VARCHAR(11): o Hibernate (ddl-auto=validate) trata CHAR e VARCHAR
-- como tipos diferentes. A restrição UNIQUE do CPF continua valendo.
ALTER TABLE pacientes ALTER COLUMN cpf TYPE VARCHAR(11);
