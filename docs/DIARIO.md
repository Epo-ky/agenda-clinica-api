# Diário do projeto

Registro do que foi feito, do que deu errado e das decisões tomadas, etapa por etapa.

> Rascunho inicial. Troque os textos pelas suas próprias palavras antes de publicar: em entrevista,
> o que vale é você conseguir contar cada decisão e cada bug como se fosse seu.

## 18/11/2026

### Etapa 1: base do projeto

**O que foi feito:** esqueleto com Spring Boot 3.4 e Java 21, Flyway com o schema inicial
(`usuarios`, `pacientes`, `profissionais`, `consultas`), endpoint de health (Actuator) e `.env.example`.

**O que deu errado**
- **Autenticação do banco (código 28P01):** a primeira subida falhou com "senha do tipo password falhou
  para o usuário postgres". O log mostrava que o Spring já tinha chegado até o Flyway, o que provou que
  o resto da configuração estava certo e que o problema era só a senha enviada à API (variável
  `DB_PASSWORD`).
- **Porta 8080 ocupada:** com a senha certa, a API caiu com "Port 8080 was already in use". Um processo
  `httpd` (Apache) usava a porta. Em vez de encerrar o programa, a porta padrão da API virou **8081**
  (configurável por `SERVER_PORT`).
- **Caminho do `psql`:** o Postgres está em `D:\Program Files\PostgreSQL\18`, não no caminho padrão do
  `C:`. Descobri olhando o caminho do serviço do Windows.
- **Maven fora do winget:** o JDK 21 instalou pelo winget, o Maven não. Baixei o zip oficial da Apache,
  conferi o hash SHA-512 e gerei o **Maven Wrapper** (`mvnw`), para quem clonar precisar só do JDK.

**Decisões**
- Segredos sempre por variável de ambiente, nunca no repositório.
- `ddl-auto: validate`: o schema é do Flyway, o Hibernate só confere.
- Security provisório liberando tudo, só para o health responder (o Spring Security bloqueia tudo por padrão).

### Etapa 2: pacientes

**O que foi feito:** CRUD com DTOs (`record`), validação própria de CPF (dígitos verificadores),
paginação, filtro por nome, exclusão lógica e um `@RestControllerAdvice` com formato de erro único.

**Decisões**
- `CHAR(11)` virou `VARCHAR(11)` numa migration (`V2`): o Hibernate com `validate` trata os dois como
  tipos diferentes. Foi uma decisão preventiva, não um erro que aconteceu.
- O CPF é salvo só com números, mesmo que venha com pontuação.
- A entidade nunca sai da API; o controller só fala com DTOs.

### Etapa 3: profissionais

**O que foi feito:** mesmo padrão de pacientes, com `enum` de especialidade (gravado como texto,
`EnumType.STRING`, nunca ordinal), duração da consulta (padrão de 30 min) e filtro por especialidade.

### Etapa 4: consultas

**O que foi feito:** agendamento com as regras 1 a 5 (horário comercial, antecedência, conflito do
profissional, paciente repetido no dia, inativos), listagem com filtros opcionais (`Specification`)
e horários livres do dia.

**O que deu errado**
- **Erros do Spring viravam 500.** Ao testar `/horarios-livres` sem o parâmetro `data`, a API devolveu
  500 em vez de 400. A causa era o `catch-all` do meu handler, que engolia também as exceções normais do
  Spring. Conferi e o mesmo acontecia com método HTTP errado (devia ser 405) e tipo de conteúdo errado
  (415). **Correção:** exceções do Spring (`ErrorResponse`) mantêm o status delas; só falha realmente
  inesperada vira 500, sem vazar detalhe.

**Decisões**
- O "agora" é um `Clock` injetado, para os testes fixarem o horário e provarem as regras de antecedência.
- Consultas canceladas não ocupam horário.

### Etapa 5: ciclo de vida

**O que foi feito:** confirmar, realizar e cancelar, com a matriz de transições dentro do próprio
`StatusConsulta`, cancelamento com motivo e 24 h de antecedência, e "realizar" só depois do início.

**O que deu errado**
- **`atualizadoEm` desatualizado na resposta.** Testando na API de verdade, o `confirmar` devolvia o
  campo vazio, e o `cancelar` devolvia o valor antigo. Eu usava `@PreUpdate` do JPA, que só roda no
  *flush* (no fim da transação), depois de a resposta já ter sido montada. **Correção:** o service grava
  o campo explicitamente, com o `Clock` injetado, e um teste cobre isso.

### Etapa 6: segurança

**O que foi feito:** login com JWT (2 h), senha com BCrypt, perfis ADMIN e RECEPCAO com `@PreAuthorize`,
401 e 403 no formato padrão de erro, Swagger com botão Authorize.

**Decisões**
- **O filtro consulta o banco a cada requisição.** Usuário inativado perde o acesso na hora (não só
  quando o token expira) e o perfil vem do banco, não do token. Custo: uma consulta por requisição.
- **Mesma mensagem para e-mail inexistente e senha errada**, e o hash é sempre conferido (mesmo sem
  usuário), para o tempo de resposta não revelar quais e-mails existem.
- **Admin inicial criado no código, não em migration SQL:** o hash precisa vir do mesmo encoder do login
  e interpolar senha em SQL é arriscado. Foi uma mudança consciente em relação ao spec.
- **`JWT_SECRET` obrigatório e sem valor padrão:** um segredo padrão no repositório seria uma brecha.
- O handler de erros ganhou tratamento de `AccessDeniedException`, senão o `@PreAuthorize` cairia no
  `catch-all` e viraria 500 (o mesmo tipo de erro da etapa 4, antecipado).
- O filtro JWT **não** é um `@Component`: como bean, ele seria registrado também fora do Spring Security.

## Limitações conhecidas

- **Corrida no agendamento:** duas requisições simultâneas para o mesmo horário podem passar da checagem
  do service. A proteção definitiva seria uma constraint de exclusão no Postgres (`btree_gist`).
- **Sem limite de tentativas de login.**
- A ordem do `@PreAuthorize` é depois da validação do corpo: um RECEPCAO com JSON inválido numa rota de
  ADMIN recebe 400, não 403.
- O Flyway avisa que o PostgreSQL 18 é mais novo que a versão testada por ele (funciona, mas convém
  atualizar a dependência).

## Pendente

- Etapa 7: testes de integração (Testcontainers), cobertura (JaCoCo), Docker e CI.
- Etapa 8: README completo e post.
