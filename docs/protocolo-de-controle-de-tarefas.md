# Protocolo de controle de tarefas

**O que é este documento.** As instruções operacionais que um agente segue para conduzir trabalho
pelo board do Jira, usando o microsserviço `jira_master`. Escrito para ser lido por um agente em
execução, não por uma pessoa.

**Onde ele vai morar ainda não está decidido** — `KAN-23` ("escolher entre `TRAINING.md` e
`CLAUDE.md`") está aberta. Este arquivo é o conteúdo, independente do recipiente. Candidato natural a
virar system prompt de sessão de execução numa versão futura, momento em que a linguagem prescritiva
abaixo passa a ser lida como instrução direta.

---

## Regra zero — o serviço não mede nada

O `jira_master` não participa da sua execução. Ele não conta seus tokens, não estima seu custo, não
infere quanto tempo você levou e não corrige número errado que você mandar.

**Você é a única fonte do seu próprio custo.** Custo que você não registrar está perdido — não existe
processo que o recupere depois, e o número que o sistema usa para decidir se continua gastando fica
menor do que a realidade. Errar para baixo aqui é o pior erro possível: autoriza gasto que não
deveria ter sido autorizado.

---

## Ciclo de vida de uma tarefa

Na ordem. Cada passo depende do anterior.

### 1. Ao começar — mover para "Em andamento"

```
POST /api/cards/{issueKey}/etapa   {"etapaDestino": "Em andamento"}
```

Antes de escrever qualquer código. Um card em "A fazer" com trabalho acontecendo é um board que
mente, e o board é o único lugar onde outra sessão consegue ver que a tarefa já tem dono.

Se não souber a etapa exata do fluxo, liste antes — os nomes são localizados e mudam por projeto:

```
GET /api/cards/{issueKey}/transicoes
```

### 2. Durante — comentar achado que muda a decisão

```
POST /api/cards/{issueKey}/comentarios   {"comentario": "..."}
```

Comentário serve para o que **outra sessão precisaria saber e não conseguiria descobrir lendo o
código**: causa-raiz medida, premissa refutada, decisão tomada e por quê. Não serve para narrar
progresso.

Se a premissa do card se mostrar errada, comente **antes** de corrigir o escopo. Um card fechado com
escopo diferente do descrito, sem registro do porquê, é uma armadilha para quem ler depois.

### 3. Ao terminar — registrar o custo

**Este passo é o que mais se esquece, e é o único irrecuperável.**

Apure o custo da sessão a partir do seu próprio transcript:

```
~/.claude/projects/<projeto-slug>/<session-id>.jsonl
```

Cada linha é um evento JSON. As mensagens `assistant` trazem um objeto `usage` com quatro contadores:
`input_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`, `output_tokens`.

**Deduplique por `message.id`** — as linhas se repetem por streaming, e somar direto infla o total em
várias vezes. Tome a última ocorrência de cada id, some os quatro contadores, e mande:

```
POST /api/cards/{issueKey}/custos
{ "modelo": "claude-opus-5",
  "inputTokens": 139, "cacheCreationTokens": 372804,
  "cacheReadTokens": 8206013, "outputTokens": 58743 }
```

Mande os **quatro** contadores separados, sempre. Não some, não arredonde, não converta para dólar
você mesmo. `cache_read` custa um décimo do input cheio e domina o volume de sessão longa — somar os
contadores de entrada numa tarifa só erra por múltiplos, não por margem.

Se a sessão tocou vários cards, atribua honestamente: registre no card onde a maior parte do trabalho
aconteceu, ou divida em linhas separadas e **diga na narrativa que foi rateio estimado**. Precisão
inventada é pior que imprecisão declarada.

### 4. Fechar

```
POST /api/cards/{issueKey}/etapa   {"etapaDestino": "Concluído"}
```

Só depois de verificar. "Verificado" significa comando rodado e saída lida — não "a mudança parece
certa". Se os testes falharam, diga com a saída; se um passo foi pulado, diga qual.

---

## Parar tudo — HOLD

**`BLOQUEADO` e `HOLD` são o mesmo estado.** Não existem dois conceitos: um mecanismo, um
vocabulário. O fluxo do `KAN` tem só `HOLD` (transição id `41`); `BLOQUEADO`, `BLOCKED` e `BLOCK` são
aceitos como sinônimos pelo serviço, para funcionar em board que nomeie a etapa de outro jeito.
Decisão registrada em `docs/superpowers/specs/2026-08-08-hold-e-bloqueado-sao-o-mesmo-estado-design.md`.

Por que parou é **motivo, não estado** — vai no comentário do card, que é onde motivo já mora.

Uma chamada para a árvore inteira:

```
POST /api/cards/{issueKeyPai}/hold   ->  200 com o resultado por card
```

Pai primeiro, filhos depois. O pai em HOLD é a flag que o health check lê; na ordem inversa existe
uma janela em que os filhos já pararam e o pai ainda aceita trabalho novo.

É idempotente: card já em HOLD volta como sucesso, com a observação. Falha parcial é **reportada, não
engolida** — a resposta diz quais moveram e quais não, com o motivo, e não há rollback. Este endpoint
precisa ser útil justamente quando as coisas já estão dando errado, e nessa hora saber o estado real
vale mais do que uma resposta binária.

### Você não sai do HOLD

Se o seu card estiver em HOLD, `POST /api/cards/{issueKey}/etapa` devolve `409` para **qualquer**
destino — o serviço olha a etapa de origem antes de qualquer outra coisa:

```json
{ "status": 409, "detail": "O card KAN-42 esta em \"HOLD\" ...", "etapaAtual": "HOLD" }
```

Não tente contornar: não existe endpoint de liberação, e criar card novo para continuar o mesmo
trabalho é a mesma violação com outro nome. HOLD significa que uma pessoa decidiu que este trabalho
para, e só uma pessoa desfaz isso, direto no board do Jira.

Recebeu `409` com `etapaAtual` de HOLD no meio de uma execução: **pare**. Registre o custo do que já
gastou (`POST /custos` continua aceito — parar não apaga a contabilidade), comente no card onde você
estava, e encerre a sessão.

---

## Subtarefas

```
POST /api/cards/{issueKeyPai}/subcards   {"titulo": "...", "descricao": "..."}
GET  /api/cards/{issueKey}/subcards      ->  200 com a lista (vazia se não tiver filhos)
```

Card sem subtarefa devolve `[]` com `200`, não `404`. Chave de subtarefa devolve `[]` pelo mesmo
motivo do parágrafo seguinte.

Profundidade máxima é **1**, e isso é de propósito: no Jira subtarefa não tem subtarefa. O teto vem
da plataforma de graça. Sentir vontade de recursão é sinal de que alguém saiu da subtarefa — e perdeu
o teto junto.

O tipo de item da subtarefa é descoberto em runtime a partir dos tipos do projeto, não fixo em
inglês. Não presuma o nome `Subtask`; num Jira em português ele é `Subtarefa`.

---

## Ler o custo da árvore

```
GET /api/cards/{issueKey}/custo   ->  200
```

Devolve `custoProprio`, `porCard[]` (com número de execuções por card), `custoTotal`,
`filhosSemCusto[]` e `linhasDescartadas`. Tudo somado **na leitura** — nenhum total é gravado.

Duas leituras que parecem iguais e não são:

| Resposta | Significa |
|---|---|
| `custoTotal: null` | **Nunca foi medido.** Não vale zero na soma de nada |
| `custoTotal: 0` | Foi medido e as linhas se anulam (houve estorno) |

`filhosSemCusto` lista os filhos sem nenhuma linha. Não é enfeite: é o que diz o quanto confiar no
total antes de decidir continuar gastando.

---

## Corrigir custo gravado errado

```
POST /api/cards/{issueKey}/custos/estornos   {"ts": "...", "card": "...", "motivo": "..."}
```

O ledger é append-only: **nada é apagado**. O estorno acrescenta uma linha com os valores negativos,
apontando a linha corrigida em `estorna`. O total volta ao certo e a auditoria continua mostrando que
houve erro e que foi corrigido.

Estorno em duplicidade é recusado com `404` — uma linha só pode ser estornada uma vez, senão o total
passa a mentir para baixo.

---

## O que nunca fazer

| Não | Porquê |
|---|---|
| Fechar card sem registrar custo | Único passo irrecuperável. O total da árvore passa a mentir para baixo |
| Somar os quatro contadores num número só antes de mandar | `cache_read` custa 0,1× do input. Erro de múltiplos, sempre para cima |
| Gravar o total em algum lugar | Total é sempre calculado na leitura. Gravado, desatualiza em silêncio |
| Tratar card sem custo como custo zero | Zero e "não medido" são coisas diferentes. Confundir autoriza gasto indevido |
| Subir uma segunda instância do serviço | A proteção contra escrita concorrente é um lock em processo. Duas instâncias perdem linha em silêncio, sem erro |
| Editar a descrição do card por fora das sentinelas de custo | O splice preserva o texto humano. Reescrever a descrição inteira apaga o ledger |
| Mover card para Concluído com teste falhando | Board que mente é pior que board vazio |

---

## Operação

- Serviço sobe com `mvn spring-boot:run` no worktree `jira-microservice-impl`. `.env` já preenchido
  (`spring-dotenv` carrega automático).
- Porta `8080` fixa. O bind é a única barreira contra segunda instância no mesmo host — não contorne.
- Corpo de requisição deve ser **UTF-8**. O serviço decodifica UTF-8 por padrão e honra `charset`
  declarado; corpo em CP1252 volta `400` com a causa do parser, não `500`.
