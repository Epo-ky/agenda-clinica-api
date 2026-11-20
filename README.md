# Agenda Clínica API

![CI](https://github.com/Epo-ky/genda-clinica-api/actions/workflows/ci.yml/badge.svg)

API REST de agendamento de consultas para uma clínica, em **Java 21 e Spring Boot 3**. Cadastra pacientes e
profissionais, marca consultas **sem conflito de horário** e controla o ciclo de vida de cada consulta
(agendada, confirmada, realizada, cancelada), com login por **JWT** e dois perfis de acesso.

**Tecnologias:** Java 21 · Spring Boot 3.4 (Web, Data JPA, Validation, Security) · PostgreSQL 16 · Flyway ·
JWT (jjwt) · springdoc-openapi (Swagger UI) · JUnit 5 · Mockito · JaCoCo · Docker · GitHub Actions

## Como rodar

### Com Docker (recomendado)

Precisa só de Docker. Um comando sobe a API e o banco.

```bash
cp .env.example .env     # depois edite o .env: DB_PASSWORD, JWT_SECRET e ADMIN_PASSWORD
docker compose up --build
```

- Swagger UI: <http://localhost:8081/swagger-ui.html>
- Health: <http://localhost:8081/actuator/health>
- A porta do host é `APP_PORT` (padrão `8081`).

### Sem Docker

Precisa de JDK 21 e de um PostgreSQL local com um banco chamado `agenda_clinica`.

```bash
# Defina as variáveis (veja o .env.example). No PowerShell:  $env:DB_PASSWORD="..."
./mvnw spring-boot:run
```

| Variável | Para que serve |
|---|---|
| `DB_PASSWORD` | senha do PostgreSQL (`DB_URL` e `DB_USER` têm padrão local) |
| `JWT_SECRET` | segredo que assina os tokens (mínimo de 32 caracteres, **sem valor padrão**) |
| `ADMIN_PASSWORD` | senha do primeiro usuário ADMIN (mínimo de 8 caracteres) |
| `ADMIN_EMAIL` | e-mail do primeiro ADMIN (padrão `admin@agendaclinica.com`) |

Na primeira subida, se ainda não existir nenhum ADMIN, a API cria um com essas credenciais. A migration `V3`
também cadastra dados de exemplo (pacientes, profissionais e consultas na semana seguinte) para a
documentação não abrir vazia.

## Experimente em 1 minuto

No Swagger UI: faça login em `POST /auth/login` com o admin, copie o `token`, clique em **Authorize** e cole.
Ou, pelo terminal:

```bash
# 1. Login (devolve um JWT válido por 2 horas)
curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@agendaclinica.com","senha":"SUA_SENHA_DE_ADMIN"}'

# 2. Horários livres de um profissional num dia útil futuro
curl -s "http://localhost:8081/profissionais/1/horarios-livres?data=AAAA-MM-DD" \
  -H "Authorization: Bearer SEU_TOKEN"

# 3. Agendar uma consulta (dia útil futuro, entre 08:00 e 18:00)
curl -s -X POST http://localhost:8081/consultas \
  -H "Authorization: Bearer SEU_TOKEN" -H "Content-Type: application/json" \
  -d '{"pacienteId":1,"profissionalId":1,"inicio":"AAAA-MM-DDT10:00:00"}'
```

## Regras de negócio

Cada regra quebrada devolve **422** com uma mensagem clara (por exemplo, `Profissional já tem consulta nesse horário`).

1. Consultas só de segunda a sexta, entre 08:00 e 18:00, e precisam terminar até as 18:00.
2. Não é possível marcar no passado nem com menos de 1 hora de antecedência.
3. Um profissional não pode ter duas consultas com horários sobrepostos (consultas canceladas não contam).
4. Um paciente não pode ter duas consultas no mesmo dia com o mesmo profissional.
5. Paciente ou profissional inativo não pode receber agendamento.
6. Transições de status permitidas: `AGENDADA → CONFIRMADA → REALIZADA` e `AGENDADA/CONFIRMADA → CANCELADA`.
7. Cancelamento exige motivo e pelo menos 24 horas de antecedência.
8. Marcar como `REALIZADA` só depois do horário de início.

A duração de cada consulta vem do profissional (padrão de 30 minutos), e o fim é `início + duração`.

## Endpoints e perfis

| Rota | O que faz | Perfil |
|---|---|---|
| `POST /auth/login` | recebe e-mail e senha, devolve o JWT | público |
| `POST /usuarios` | cria usuário | ADMIN |
| `GET /pacientes?nome=&page=` | lista paginada com filtro por nome | ADMIN, RECEPCAO |
| `GET /pacientes/{id}` · `POST` · `PUT` | detalha, cadastra, atualiza | ADMIN, RECEPCAO |
| `DELETE /pacientes/{id}` | inativa (exclusão lógica) | ADMIN |
| `GET /profissionais?especialidade=` | lista com filtro por especialidade | ADMIN, RECEPCAO |
| `POST` · `PUT` · `DELETE /profissionais` | escrita | ADMIN |
| `GET /profissionais/{id}/horarios-livres?data=` | horários disponíveis no dia | ADMIN, RECEPCAO |
| `POST /consultas` | agenda, aplicando as regras 1 a 5 | ADMIN, RECEPCAO |
| `GET /consultas?profissionalId=&pacienteId=&data=&status=&page=` | lista com filtros e paginação | ADMIN, RECEPCAO |
| `PATCH /consultas/{id}/confirmar` · `realizar` · `cancelar` | ciclo de vida (regras 6 a 8) | ADMIN, RECEPCAO |

Sem token a resposta é **401**; com o perfil errado, **403**. Todos os erros seguem o mesmo formato
(`timestamp`, `status`, `erro`, `mensagem`, `caminho` e a lista de `campos` inválidos).

## Como funciona

```mermaid
flowchart LR
    C[Cliente] --> F[Filtro JWT]
    F --> K[Controller<br/>@PreAuthorize por perfil]
    K --> S[Service<br/>regras de negócio]
    S --> R[Repository<br/>Spring Data JPA]
    R --> DB[(PostgreSQL<br/>schema via Flyway)]
```

- **Camadas por funcionalidade** (`paciente`, `profissional`, `consulta`, `usuario`, `auth`). Controllers falam só com
  DTOs (`record`); a entidade nunca sai da API. As regras ficam nos services.
- **Segurança:** senha com BCrypt; token JWT assinado (HS256) com validade de 2 horas; o filtro **confere o usuário no
  banco a cada requisição**, então quem é inativado perde o acesso na hora e o perfil nunca vem só do token. O login
  responde igual para e-mail inexistente e senha errada, para não revelar quais e-mails existem.
- **O "agora" é injetável** (`Clock`), o que permite testar as regras de antecedência com horário fixo.
- **Migrations com Flyway**; o Hibernate só confere o schema (`ddl-auto: validate`).

## Testes

```bash
./mvnw test      # 114 testes unitários (JUnit 5 + Mockito), sem banco
./mvnw verify    # + 15 testes de integração contra um PostgreSQL de verdade + meta de cobertura
```

- Os testes de integração sobem a aplicação inteira e cobrem login, agendamento, conflito de horário, ciclo de vida
  e acesso negado por perfil. Eles usam um banco **separado** (`agenda_clinica_test`, configurável por `TEST_DB_URL`,
  `TEST_DB_USER` e `TEST_DB_PASSWORD`) e se recusam a rodar em qualquer banco cujo nome não termine em `_test`.
- **Cobertura (JaCoCo):** 99% das linhas nos services, com meta mínima de 80% verificada no `verify`.
  O relatório fica em `target/site/jacoco/index.html`.
- **CI (GitHub Actions):** a cada push, roda o `verify` com um PostgreSQL de serviço e depois prova que
  `docker compose up` sobe a API do zero, faz login e chama uma rota protegida.

## O que deu errado no caminho

Os problemas reais e as decisões de cada etapa estão em [docs/DIARIO.md](docs/DIARIO.md). Dois exemplos:

- Erros normais do Spring (parâmetro ausente, método HTTP errado) viravam **500** por causa do meu handler genérico.
  Descobri testando a API de verdade; agora mantêm o status certo (400, 405, 415...).
- O campo `atualizadoEm` voltava vazio na resposta porque o callback `@PreUpdate` só roda no fim da transação,
  depois de a resposta ser montada. Passou a ser gravado explicitamente pelo service.

## Limitações conhecidas

- Duas requisições simultâneas para o mesmo horário podem passar da checagem do service; a proteção definitiva seria
  uma constraint de exclusão no PostgreSQL (`btree_gist`).
- Não há limite de tentativas de login.
- Os testes de integração usam o PostgreSQL local (ou o serviço do CI), não Testcontainers.

## Sobre o desenvolvimento

Projeto de portfólio, desenvolvido com o **Claude Code** como assistente de programação. As decisões técnicas e
os problemas encontrados em cada etapa estão registrados no [diário do projeto](docs/DIARIO.md).
