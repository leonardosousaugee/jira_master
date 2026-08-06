# Microsserviço Jira — design

Data: 2026-08-05

## Objetivo

Microsserviço Java/Spring que expõe uma API REST para operar cards (issues) do Jira Cloud,
substituindo integração futura direta do kanban (`JuditeCompany`) com o Jira. É um projeto novo
e independente, sem dependência de código do kanban Node existente.

## Stack

- Java 21, Spring Boot 3.3.x, Maven
- Spring Web (servlet clássico — sem WebFlux; volume de tráfego não justifica reativo)
- `RestClient` (Spring 6) para chamar a Jira REST API v3
- springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) para Swagger UI em `/swagger-ui.html`
- `spring-dotenv` (`me.paulschwarz:spring-dotenv`) para carregar `.env` automaticamente no boot
- JUnit 5 + Mockito para testes unitários

Sem banco de dados: o microsserviço é um proxy stateless para o Jira — o Jira é a fonte da
verdade, não há necessidade de persistência local.

## Estrutura de pacotes

```
com.juditecompany.jiramaster
├── controller       endpoints REST
├── service          regra de negócio, orquestra chamadas ao client
├── client           JiraApiClient — HTTP puro contra a Jira REST API v3
├── dto
│   ├── request       payloads de entrada (records)
│   └── response      payloads de saída (records)
├── mapper           JiraCardMapper (Jira <-> DTO interno), AdfMapper (texto <-> ADF)
├── exception        exceções de domínio + GlobalExceptionHandler
└── config           JiraProperties, RestClientConfig, OpenApiConfig
```

## Contrato de API

| Operação | Endpoint | Jira REST API v3 |
|---|---|---|
| criarCard | `POST /api/cards` | `POST /issue` |
| lerCardsEmAberto | `GET /api/cards/abertos?projectKey=` | `GET /search` — JQL `project = {key} AND statusCategory != Done` |
| buscarCardPorId | `GET /api/cards/{issueKey}` | `GET /issue/{key}` |
| editarCard | `PATCH /api/cards/{issueKey}` | `PUT /issue/{key}` (título/descrição) |
| listarTransicoesDisponiveis | `GET /api/cards/{issueKey}/transicoes` | `GET /issue/{key}/transitions` |
| alterarEtapaCard | `POST /api/cards/{issueKey}/etapa` | resolve nome → transitionId, depois `POST /issue/{key}/transitions` |
| adicionarSubCard | `POST /api/cards/{issueKey}/subcards` | `POST /issue` com `fields.parent.key` |
| editarPrioridade | `PATCH /api/cards/{issueKey}/prioridade` | `PUT /issue/{key}` campo `priority` |
| adicionarComentario | `POST /api/cards/{issueKey}/comentarios` | `POST /issue/{key}/comment` |

Projeto Jira padrão: `KAN` (configurável via `.env`, pode ser sobrescrito por request quando
aplicável, ex: `lerCardsEmAberto`).

### Pontos não óbvios

- **ADF**: a Jira Cloud v3 exige descrição e corpo de comentário em Atlassian Document Format,
  não texto puro. `AdfMapper` embrulha uma string simples em um documento ADF mínimo (um
  parágrafo); a conversão fica isolada no client/mapper, a API do microsserviço só recebe/retorna
  texto simples.
- **Transições**: a Jira não aceita nome de status na chamada de transição, só o ID da transição
  (que varia por issue/workflow). `alterarEtapaCard` recebe o nome da etapa destino em português
  claro (ex.: `"Em andamento"`), o service busca as transições disponíveis via
  `listarTransicoesDisponiveis`, casa pelo nome (case-insensitive) e só então dispara a transição.
  Se não encontrar, retorna 400 com a lista de nomes de transições válidas para aquele card.

## DTOs (records)

Request:
- `CriarCardRequest(titulo, descricao, tipoIssue, projectKey)` — `tipoIssue` padrão `"Task"`,
  `projectKey` opcional (usa o default de `.env` se ausente)
- `EditarCardRequest(titulo, descricao)` — campos nuláveis, PATCH parcial
- `AlterarEtapaRequest(etapaDestino)`
- `AdicionarSubCardRequest(titulo, descricao)`
- `EditarPrioridadeRequest(prioridade)`
- `AdicionarComentarioRequest(comentario)`

Response:
- `CardResponse(issueKey, titulo, descricao, status, prioridade, tipoIssue, projectKey, criadoEm, atualizadoEm)`
- `CardResumoResponse(issueKey, titulo, status, prioridade)` — usado na listagem
- `TransicaoResponse(id, nomeEtapaDestino)`
- `ComentarioResponse(id, autor, corpo, criadoEm)`

Erros usam `ProblemDetail` (RFC 7807) nativo do Spring — sem DTO de erro customizado.

## Autenticação e configuração

`.env` na raiz do projeto (fora do controle de versão, `.gitignore` já cobre):

```
JIRA_BASE_URL=https://juditecompany.atlassian.net
JIRA_EMAIL=leonardo.sousa@witzler-ultragaz.com.br
JIRA_API_TOKEN=<token>
JIRA_DEFAULT_PROJECT_KEY=KAN
SERVER_PORT=8080
```

`.env.example` no repositório sem valores reais, como referência.

`JiraProperties` (`@ConfigurationProperties(prefix = "jira")`, `@Validated`) lê essas variáveis
via `application.yml`. Campos obrigatórios ausentes fazem o boot falhar imediatamente — não
silenciosamente.

Basic Auth: `RestClient` configurado com um `ClientHttpRequestInterceptor` que adiciona
`Authorization: Basic base64(email:token)` em toda chamada. Token nunca aparece em log.

## Tratamento de erros

Exceções de domínio:
- `CardNotFoundException` → 404
- `TransitionNotFoundException` (inclui lista de transições válidas) → 400
- `JiraApiException` (falha na chamada HTTP à Jira) → propaga o status HTTP retornado pela Jira,
  detail com a mensagem de erro da Jira
- Erros de validação de request (`jakarta.validation`) → 400, com os campos inválidos listados

`GlobalExceptionHandler` (`@RestControllerAdvice`) centraliza a tradução dessas exceções em
`ProblemDetail`. Exceções não mapeadas caem em 500 genérico, sem expor stack trace ou detalhes
internos na resposta.

## Testes

JUnit 5 + Mockito, unitários, mockando `JiraApiClient` (sem chamada real à Jira em teste):
- `JiraCardServiceImpl`: cada operação, incluindo resolução de transição (sucesso e "transição
  não encontrada") e construção da JQL de `lerCardsEmAberto`
- `AdfMapper`: texto simples → estrutura ADF esperada
- Controllers via `@WebMvcTest`, service mockado, verificação de status HTTP e shape de resposta

Fora de escopo neste MVP: testes de integração contra Jira real (exigiriam rede e token válido
em CI) — ficam para uma rodada futura, se necessário.

## Swagger

`springdoc-openapi-starter-webmvc-ui` expõe `/swagger-ui.html` e `/v3/api-docs`. Serve como forma
principal de teste manual ponta a ponta contra o Jira real durante o desenvolvimento.
