# jira_master

Microsserviço REST que expõe as operações de card do Jira Cloud como uma API estável e enxuta,
pensada para ser consumida por agentes automatizados — não por humanos clicando no board.

O Jira é a fonte da verdade do trabalho: cada tarefa é um card, cada subtarefa é um sub-card, e o
custo de execução fica gravado no próprio card. Este serviço é a única porta de entrada para isso.
O agente que executa trabalho não fala com a API do Jira diretamente; fala com o `jira_master`, que
traduz, valida e mantém as invariantes que o Jira sozinho não garante.

---

## Índice

- [Por que existe](#por-que-existe)
- [Arquitetura](#arquitetura)
- [Como rodar](#como-rodar)
- [Configuração](#configuração)
- [Endpoints](#endpoints)
- [Worker — quem executa o card](#worker--quem-executa-o-card)
- [Tarifas — a tabela como serviço](#tarifas--a-tabela-como-serviço)
- [Ledger de custo](#ledger-de-custo)
- [HOLD — o kill switch da árvore](#hold--o-kill-switch-da-árvore)
- [Erros](#erros)
- [Testes](#testes)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Documentação](#documentação)

---

## Por que existe

A API do Jira Cloud é ampla, verbosa e cheia de detalhe de plataforma: ADF para texto, transições
por id numérico descoberto em runtime, tipos de item que variam por projeto, JQL para qualquer
leitura não trivial. Um agente que precisa apenas "criar card", "mover para HOLD" ou "registrar o
que essa execução custou" não deveria carregar nada disso.

O `jira_master` resolve três coisas que a API bruta não resolve:

1. **Vocabulário do domínio.** Endpoints falam de card, sub-card, etapa e custo — não de issue,
   ADF, transition id e customfield.
2. **Invariantes de custo.** Custo é gravado como linhas auditáveis dentro do card, nunca como um
   total mutável. Estorno acrescenta linha negativa em vez de apagar histórico.
3. **Falha parcial visível.** Operações que tocam vários cards (como o HOLD em árvore) reportam
   card a card o que funcionou e o que não funcionou, em vez de devolver um booleano que esconde
   metade do resultado.

## Arquitetura

Spring Boot 3.3, Java 21. Sem banco de dados — o estado mora no Jira.

```
controller/   JiraCardController          camada HTTP, /api/cards
              TarifaController            camada HTTP, /api/tarifas
service/      JiraCardService(+Impl)      regras: custo, HOLD, descoberta de transição
custo/        CalculadoraDeCusto          a única tabela de tarifas em código, dois chamadores
ledger/       LedgerDeCusto, LinhaCusto   leitura e escrita do bloco de custo na descrição
mapper/       JiraCardMapper, AdfMapper   Jira <-> domínio; texto <-> ADF
client/       JiraApiClient + DTOs        chamadas HTTP à API do Jira Cloud
config/       JiraProperties, TarifaProperties, RestClientConfig, OpenApiConfig
exception/    GlobalExceptionHandler      exceções de domínio -> RFC 7807 ProblemDetail
```

O fluxo de uma requisição é sempre o mesmo: controller valida o corpo, service aplica a regra,
client fala com o Jira, mapper traduz a resposta. Nenhuma camada pula a seguinte.

## Como rodar

Pré-requisitos: **JDK 21** e **Maven 3.9+**.

```bash
git clone https://bitbucket.org/juditecompany/jira_master.git
cd jira_master
cp .env.example .env    # preencha as credenciais
mvn spring-boot:run
```

Sobe em `http://localhost:8080`.

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Empacotar:

```bash
mvn clean package
java -jar target/jira-master-service-1.0.0.jar
```

## Configuração

Variáveis lidas do `.env` na raiz (via `spring-dotenv`) ou do ambiente. O `.env` está no
`.gitignore` — **nunca comite credencial**.

| Variável | Obrigatória | Padrão | O que é |
|---|---|---|---|
| `JIRA_BASE_URL` | sim | — | URL da instância, ex. `https://empresa.atlassian.net` |
| `JIRA_EMAIL` | sim | — | E-mail da conta Atlassian usada na autenticação |
| `JIRA_API_TOKEN` | sim | — | API token da conta ([gerar aqui](https://id.atlassian.com/manage-profile/security/api-tokens)) |
| `JIRA_DEFAULT_PROJECT_KEY` | não | `KAN` | Projeto usado quando a requisição não informa um |
| `JIRA_SUBTASK_ISSUE_TYPE_ID` | não | vazio | Id do tipo de subtarefa. Em branco, é descoberto em runtime lendo os tipos do projeto — preencha só quando o projeto tem mais de um tipo de subtarefa e a descoberta escolhe o errado |
| `JIRA_WORKER_FIELD_ID` | não | vazio | Id do campo customizado Worker, ex. `customfield_10073`. Em branco, é descoberto em runtime lendo os campos da instância e casando pelo nome `Worker` — preencha só quando o campo foi renomeado ou existe mais de um com esse nome |
| `SERVER_PORT` | não | `8080` | Porta HTTP |

As três primeiras são validadas na subida: faltando qualquer uma, a aplicação não inicia.

As tarifas de custo por modelo ficam em `src/main/resources/application.yml`, sob `custo.tarifas`,
em dólares por milhão de tokens, com a data da tabela em `custo.tabela-versao`. Preço muda e modelo
novo aparece — por isso configuração e não literal no código. Ver
[Tarifas — a tabela como serviço](#tarifas--a-tabela-como-serviço).

## Endpoints

Base: `/api/cards`

### Cards

| Método | Rota | O que faz | Sucesso |
|---|---|---|---|
| `POST` | `/api/cards` | Cria um card | `201` |
| `GET` | `/api/cards/abertos?projectKey=` | Lista cards não concluídos. Sem `projectKey`, usa o projeto padrão | `200` |
| `GET` | `/api/cards/{issueKey}` | Lê um card, já com o custo separado da descrição | `200` |
| `PATCH` | `/api/cards/{issueKey}` | Edita título, descrição e/ou worker | `200` |
| `PATCH` | `/api/cards/{issueKey}/prioridade` | Altera a prioridade | `204` |

### Fluxo

| Método | Rota | O que faz | Sucesso |
|---|---|---|---|
| `GET` | `/api/cards/{issueKey}/transicoes` | Lista as transições possíveis a partir do estado atual | `200` |
| `POST` | `/api/cards/{issueKey}/etapa` | Move o card para a etapa informada, por nome | `204` |
| `POST` | `/api/cards/{issueKey}/hold` | Move o card **e toda a sua árvore** para HOLD | `200` |

### Sub-cards

| Método | Rota | O que faz | Sucesso |
|---|---|---|---|
| `POST` | `/api/cards/{issueKey}/subcards` | Cria uma subtarefa sob o card | `201` |
| `GET` | `/api/cards/{issueKey}/subcards` | Lista as subtarefas do card | `200` |

### Custo

| Método | Rota | O que faz | Sucesso |
|---|---|---|---|
| `POST` | `/api/cards/{issueKey}/custos` | Registra uma execução e devolve a linha gravada | `201` |
| `GET` | `/api/cards/{issueKey}/custo` | Soma o custo do card e de toda a árvore | `200` |
| `POST` | `/api/cards/{issueKey}/custos/estornos` | Estorna uma linha, acrescentando a negativa | `201` |

### Tarifas

| Método | Rota | O que faz | Sucesso |
|---|---|---|---|
| `GET` | `/api/tarifas/custo?modelo=&entradaNova=&...` | Converte contadores de token em dólar. Cálculo puro — não grava nada | `200` |

### Comentários

| Método | Rota | O que faz | Sucesso |
|---|---|---|---|
| `POST` | `/api/cards/{issueKey}/comentarios` | Adiciona um comentário | `201` |

### Exemplos

Criar um card:

```bash
curl -X POST http://localhost:8080/api/cards \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"titulo":"Migrar autenticação","descricao":"Trocar basic auth por OAuth","tipoIssue":"Tarefa","worker":"agente-alpha"}'
```

## Worker — quem executa o card

`worker` é o campo customizado `Worker` do Jira, exposto como campo de primeira classe da API:
aceito em `POST /api/cards`, `PATCH /api/cards/{issueKey}` e `POST /api/cards/{issueKey}/subcards`,
e devolvido em `CardResponse` e na listagem de cards abertos.

O id do campo muda de instância para instância, então ele é descoberto em runtime pelo nome
(`GET /field`, casando por `Worker`, sem diferenciar maiúsculas) e guardado em cache no processo —
uma consulta por subida, não uma por card. `JIRA_WORKER_FIELD_ID` fixa o id e pula a descoberta.

Duas regras que valem a pena saber antes de integrar:

- **Na leitura, ausente é `null`.** Campo não configurado na instância, valor nulo ou string em
  branco — os três viram `worker: null`. Nenhum deles derruba a requisição.
- **Na escrita, `null` não toca no valor; string vazia limpa.** E mandar um `worker` numa instância
  que não tem o campo devolve `422` em vez de criar o card sem dono: um card gravado sem o worker
  que o chamador pediu esconde a perda até o momento em que ninguém mais sabe de quem era o
  trabalho.

```bash
curl -X PATCH http://localhost:8080/api/cards/KAN-42 \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"worker":"agente-beta"}'
```

Registrar o custo de uma execução — os contadores vão **separados**, sem soma e sem conversão para
dólar do lado de quem chama. `cacheCreation1hTokens` é opcional e vale zero por omissão:

```bash
curl -X POST http://localhost:8080/api/cards/KAN-42/custos \
  -H 'Content-Type: application/json' \
  -d '{"modelo":"claude-opus-5","inputTokens":12000,"cacheCreationTokens":8000,"cacheCreation1hTokens":0,"cacheReadTokens":150000,"outputTokens":3000}'
```

Ler o custo da árvore:

```bash
curl http://localhost:8080/api/cards/KAN-42/custo
```

```json
{
  "issueKey": "KAN-42",
  "custoProprio": 0.2185,
  "porCard": [
    { "issueKey": "KAN-42", "custo": 0.2185, "execucoes": 2 },
    { "issueKey": "KAN-43", "custo": 0.0412, "execucoes": 1 }
  ],
  "custoTotal": 0.2597,
  "filhosSemCusto": ["KAN-44"],
  "linhasDescartadas": 0
}
```

## Tarifas — a tabela como serviço

A tabela de preços em dólar vive num lugar só, em código, e é consultável por máquina. O agente que
executa trabalho manda contadores de token e não precisa carregar preço nenhum:

```bash
curl 'http://localhost:8080/api/tarifas/custo?modelo=claude-opus-5&entradaNova=120000&entradaCacheLida=850000&entradaCacheEscrita1h=40000&saida=6000'
```

```json
{
  "modelo": "claude-opus-5",
  "custoUsd": 1.5750,
  "detalhe": {
    "entradaNova": 0.6000,
    "entradaCacheLida": 0.4250,
    "entradaCacheEscrita5m": 0.0000,
    "entradaCacheEscrita1h": 0.4000,
    "saida": 0.1500
  },
  "tabelaVersao": "2026-06-24"
}
```

É **cálculo puro**: não grava, não move card, não escreve linha de custo. `POST /custos` continua
calculando por dentro — os dois caminhos compartilham a mesma calculadora, então `getCusto` fora do
ar atrapalha estimativa e nunca a gravação do ledger.

### Cinco contadores, não dois

Cada componente tem multiplicador próprio sobre a tarifa de entrada. Somar tudo como "entrada" infla
o número em quase dez vezes numa sessão longa, onde a maior parte da entrada é leitura de cache.

| Contador | Multiplicador sobre a entrada |
|---|---|
| `entradaNova` | 1× |
| `entradaCacheLida` | 0,1× |
| `entradaCacheEscrita5m` | 1,25× |
| `entradaCacheEscrita1h` | 2× |
| `saida` | tarifa de saída (≈ 5× a de entrada) |

Contador ausente vale zero. **Todos ausentes vale `400`**, não `custoUsd: 0` — zero medido e não
medido são coisas diferentes.

### `tabelaVersao`

Sai em toda resposta. Sem ela, uma mudança de preço reescreve retroativamente o significado de todo
número já calculado: o resultado precisa dizer com que insumo foi produzido. A versão e os preços
ficam em `application.yml`, sob `custo`.

### Variante de modelo

**String de modelo desconhecida é recusada com `422`, nunca cobrada com o preço da base.** Aceitar a
variante e cobrar o preço-base gravaria número errado com cara de certo — o pior resultado possível
para um ledger. Duas razões concretas por que a regra não pode ser "tira o sufixo e usa a base":

- **Contexto de 1M não tem prêmio** — a tarifa é a mesma da base, então `claude-opus-5` já cobre.
- **Fast mode custa o dobro** — por isso `claude-opus-5-fast` é linha própria na tabela.

Modelo novo entra acrescentando uma linha em `custo.tarifas`; até lá, o serviço recusa.

> **Nota de preço:** `claude-sonnet-5` está com promoção de entrada até 2026-08-31 (US$ 2,00 /
> US$ 10,00 por milhão). A tabela usa o preço de lista (US$ 3,00 / US$ 15,00); troque em
> `application.yml` se a estimativa precisar do promocional.

Especificação de origem: [`docs/spec-tarifas-getcusto.md`](docs/spec-tarifas-getcusto.md).

## Ledger de custo

Custo não mora em campo customizado nem em banco: mora na **descrição do próprio card**, dentro de
um bloco delimitado por sentinelas.

```
Texto humano da descrição, preservado byte a byte.

<!-- custo:v1 -->
{"ts":"2026-08-08T14:03","card":"KAN-42","in":170000,"out":3000,"usd":0.2185}
{"ts":"2026-08-08T15:10","card":"KAN-42","in":22000,"out":800,"usd":0.0410}
<!-- /custo -->
```

As decisões que sustentam esse formato:

- **O bloco vive no card pai.** Linhas de execução de subtarefas ficam gravadas no pai, com o campo
  `card` dizendo qual subtarefa gastou. Um card só — o topo da árvore — carrega o histórico
  inteiro, então somar a árvore é uma leitura, não uma varredura. Consultar o custo de uma
  subtarefa lê o bloco do pai e filtra pelas linhas dela.
- **Uma linha JSON por execução, append-only.** O total nunca é gravado; é somado na leitura. Total
  gravado desincroniza, linha não.
- **Tudo fora das sentinelas sobrevive intacto.** A escrita faz splice só do miolo, então texto
  humano na descrição nunca é perdido.
- **Os quatro contadores vêm separados de quem chama.** Cache read custa um décimo do input cheio e
  domina o volume de sessão longa; somar tudo numa tarifa só erra por multiplicadores, sempre para
  cima. O campo `in` da linha é volume lido, não custo — o custo está em `usd` e só nele.
- **Estorno acrescenta, não apaga.** `POST /custos/estornos` grava uma linha nova com valores
  negativos e o campo `estorna` apontando o `ts` da linha corrigida. O total volta ao certo e a
  auditoria mantém o registro do erro.
- **Linha malformada é ignorada e contada,** nunca derruba a leitura — um caractere torto num card
  não pode cegar o custo da árvore inteira. A contagem sai em `linhasDescartadas`.
- **Zero e "não medido" são coisas diferentes.** `custoTotal` é nulo quando não existe nenhuma
  linha, e `filhosSemCusto` lista os cards da árvore sem custo registrado. Mentir para baixo no
  número que autoriza continuar gastando é o pior erro possível aqui.

## HOLD — o kill switch da árvore

`POST /api/cards/{issueKey}/hold` para o card e todos os seus sub-cards.

- **`BLOQUEADO` e `HOLD` são o mesmo estado.** O fluxo do KAN só tem `HOLD`; `BLOQUEADO`, `BLOCKED`
  e `BLOCK` são reconhecidos como sinônimos.
- **Pai primeiro, filhos depois.** O pai em HOLD é a flag que o health check entre blocos lê.
- **Falha parcial é reportada, nunca engolida nem revertida em silêncio.** A resposta traz uma
  entrada por card com `movido` e uma `observacao` explicando o motivo — este endpoint precisa ser
  útil justamente quando as coisas já estão dando errado, e nessa hora saber quais cards pararam
  vale mais que uma resposta binária.

```json
{
  "resultados": [
    { "issueKey": "KAN-42", "movido": true, "observacao": null },
    { "issueKey": "KAN-43", "movido": true, "observacao": "ja estava em HOLD" },
    { "issueKey": "KAN-44", "movido": false, "observacao": "nenhuma transicao para HOLD a partir de \"Concluído\"" }
  ]
}
```

## Erros

Toda falha sai como `ProblemDetail` (RFC 7807), com campos extras quando ajudam a corrigir a
chamada.

| Situação | Status | Campo extra |
|---|---|---|
| Card não encontrado | `404` | — |
| Linha de custo a estornar não encontrada | `404` | — |
| Etapa de destino inexistente | `400` | `transicoesDisponiveis` |
| Corpo inválido (validação) | `400` | `erros` |
| Corpo ilegível (JSON quebrado, encoding errado) | `400` | — |
| Modelo sem tarifa configurada | `422` | `modelosConhecidos` |
| Tipo de subtarefa não encontrado no projeto | `422` | `tiposDisponiveis` |
| `worker` informado numa instância sem o campo Worker | `422` | — |
| `GET /api/tarifas/custo` sem nenhum contador de token | `400` | — |
| Erro repassado pela API do Jira | status do Jira | — |
| Qualquer outra falha | `500` | — |

O handler de corpo ilegível existe de propósito: sem ele, um `Content-Type` com charset errado
viraria `500` mudo e esconderia a causa real.

## Testes

```bash
mvn test
```

78 testes, sem dependência de rede — o client do Jira é exercitado contra `MockRestServiceServer`.

## Estrutura do repositório

```
src/main/java/...     código da aplicação
src/main/resources/   application.yml (tarifas, portas, springdoc)
src/test/java/...     suíte de testes
docs/                 protocolo operacional e specs de design
.env.example          modelo de configuração
```

## Documentação

- [`docs/protocolo-de-controle-de-tarefas.md`](docs/protocolo-de-controle-de-tarefas.md) —
  instruções operacionais que um agente segue para conduzir trabalho pelo board. Escrito para ser
  lido por um agente em execução.
- [`docs/superpowers/specs/`](docs/superpowers/specs/) — specs de design, uma por decisão:
  desenho geral do microsserviço, ledger de custo no card, e a equivalência entre HOLD e BLOQUEADO.
- [`docs/superpowers/plans/`](docs/superpowers/plans/) — plano de implementação seguido na
  construção.
- [`TODO-ledger-de-custo.md`](TODO-ledger-de-custo.md) — pacote de trabalho do ledger.
