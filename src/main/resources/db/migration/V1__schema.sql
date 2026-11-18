CREATE TABLE usuarios (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(160) NOT NULL UNIQUE,
    senha_hash  VARCHAR(100) NOT NULL,
    perfil      VARCHAR(20)  NOT NULL CHECK (perfil IN ('ADMIN', 'RECEPCAO')),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE pacientes (
    id               BIGSERIAL PRIMARY KEY,
    nome             VARCHAR(160) NOT NULL,
    cpf              CHAR(11)     NOT NULL UNIQUE,
    data_nascimento  DATE         NOT NULL,
    telefone         VARCHAR(20),
    email            VARCHAR(160),
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE profissionais (
    id                    BIGSERIAL PRIMARY KEY,
    nome                  VARCHAR(160) NOT NULL,
    registro_conselho     VARCHAR(30)  NOT NULL UNIQUE,
    especialidade         VARCHAR(40)  NOT NULL,
    duracao_consulta_min  INTEGER      NOT NULL DEFAULT 30 CHECK (duracao_consulta_min > 0),
    ativo                 BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE consultas (
    id                   BIGSERIAL PRIMARY KEY,
    paciente_id          BIGINT       NOT NULL REFERENCES pacientes (id),
    profissional_id      BIGINT       NOT NULL REFERENCES profissionais (id),
    inicio               TIMESTAMP    NOT NULL,
    fim                  TIMESTAMP    NOT NULL,
    status               VARCHAR(20)  NOT NULL
                         CHECK (status IN ('AGENDADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA')),
    motivo_cancelamento  VARCHAR(255),
    criado_em            TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMP,
    CHECK (fim > inicio)
);

CREATE INDEX idx_consultas_profissional_inicio ON consultas (profissional_id, inicio);
CREATE INDEX idx_consultas_paciente_inicio ON consultas (paciente_id, inicio);
