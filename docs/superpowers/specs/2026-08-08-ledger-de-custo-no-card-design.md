# Spec — ledger de custo no card

**Card:** `KAN-25` — "Definir onde o custo total mora no card" (uma das questões pequenas da §7.2,
sob `KAN-7`).
**Origem:** sessão de brainstorming de 2026-08-08, conduzida em `C:\Projetos\jira_master`.
**Destrava:** `KAN-18` ("ler custo agregado da árvore"), que estava explicitamente bloqueada por esta
decisão.
**Status:** decidido. Implementação pendente — ver `TODO-ledger-de-custo.md`.

---

## Decisão em uma frase

O custo de cada execução é gravado como **uma linha JSON**, num bloco delimitado dentro da
**descrição do card pai**, e **quem grava é sempre o agente que executou** — o serviço nunca mede
nada.

---

## 1. O contrato: quem manda o valor

**Esta é a parte que não pode ser esquecida ao implementar ou ao operar.**

O microsserviço `jira_master` **não participa da execução** e portanto **não tem como saber quanto
custou coisa alguma**. Ele não conta tokens, não estima, não infere e não corrige. Ele recebe um
número pronto, calcula o USD com as tarifas do modelo informado, e grava.

Isso significa que **o agente que executou a tarefa é o único que pode registrar o custo dela**, e
que um custo não registrado é um custo perdido — não existe nenhum processo que o recupere depois.

### De onde o agente tira o número

Do transcript da própria sessão do Claude Code:

```
~/.claude/projects/<projeto-slug>/<session-id>.jsonl
```

Cada linha é um evento JSON. As linhas de mensagem `assistant` carregam um objeto `usage` com quatro
contadores:

```json
{"input_tokens": 139,
 "cache_creation_input_tokens": 372804,
 "cache_read_input_tokens": 8206013,
 "output_tokens": 58743}
```

**As linhas se repetem** (eventos parciais de streaming). Agregar **deduplicando por `message.id`**,
tomando a última ocorrência de cada id — somar tudo direto infla o total em várias vezes.

### Como o USD é calculado

Com os **quatro** contadores, cada um na sua tarifa. O que importa aqui é a relação entre eles, que
não muda quando o preço muda:

| Contador | Tarifa |
|---|---|
| `input_tokens` | tarifa de entrada do modelo, 1× |
| `output_tokens` | tarifa de saída, ≈ 5× a de entrada |
| `cache_creation_input_tokens` | 1,25× a entrada (TTL 5min); 2× no TTL de 1h |
| `cache_read_input_tokens` | 0,1× a entrada |

Os valores em dólar não estão neste documento de propósito: moram em `custo.tarifas`
(`application.yml`) e se leem por `GET /api/tarifas/custo`. Cada modelo tem os seus. Por isso o
**USD é calculado no momento da escrita**, com as tarifas do modelo que rodou, e gravado pronto. Não
se recalcula custo a partir das colunas da linha depois.

### Por que os quatro, se a linha só mostra dois

Porque `cache_read` custa **um décimo** do input cheio, e numa sessão agêntica longa ele domina o
volume. Exemplo real, medido na sessão que produziu esta spec:

| | |
|---|---|
| `input_tokens` | 139 |
| `cache_creation_input_tokens` | 372.804 |
| `cache_read_input_tokens` | 8.206.013 |
| `output_tokens` | 58.743 |
| **USD correto (quatro tarifas)** | **1×** |
| USD se `cache_read` fosse cobrado como input cheio | **5,7×** |

**5,7× de erro.** Um ledger que soma os contadores de entrada numa tarifa só não erra na margem —
erra por múltiplos, e erra sempre para cima, no número que existe justamente para autorizar ou negar
gasto adicional.

---

## 2. Onde mora

Bloco delimitado na **descrição do card pai**, dentro de um nó ADF `codeBlock`:

```
<!-- custo:v1 -->
{"ts":"2026-08-08T13:04","card":"KAN-15","in":12000,"out":4200,"usd":0.24}
{"ts":"2026-08-08T15:20","card":"KAN-15","in":31000,"out":11500,"usd":0.63}
{"ts":"2026-08-08T16:02","card":"KAN-16","in":95000,"out":18300,"usd":1.42}
<!-- /custo -->
```

**Regras de posição:**

- O bloco vive no card **pai** (a frente). Subtarefa **não tem bloco próprio** — o gasto dela é uma
  linha dentro do pai, marcada com a chave dela no campo `card`.
- Card sem pai carrega o bloco de si mesmo.
- Tudo que estiver na descrição **fora** das sentinelas é texto humano e é preservado intacto. A
  escrita faz splice apenas entre os marcadores.

**Por que `codeBlock` e não texto solto:** ADF não renderiza `\n` dentro de um nó `text` — precisaria
de nós `hardBreak` explícitos. Um bloco multi-linha escrito como parágrafo vira uma linha só,
ilegível na tela do Jira. `codeBlock` preserva quebras, renderiza monoespaçado, e faz round-trip
limpo pelo `AdfMapper.adfParaTexto` atual.

**`v1` no marcador é obrigatório.** É o que permite mudar o formato depois sem que o leitor novo
engasgue em bloco velho.

---

## 3. Formato da linha

Uma linha JSON por **execução** — não por card. Uma subtarefa reexecutada (retry, volta de HOLD,
segunda sessão) gera uma linha nova. Ver duas linhas do mesmo card é o sinal de retrabalho.

| Campo | Tipo | Significado |
|---|---|---|
| `ts` | string | `YYYY-MM-DDTHH:MM`. Minuto, sem segundos, sem timezone |
| `card` | string | Chave do card que gastou (pode ser o próprio pai) |
| `in` | inteiro | `input_tokens` + `cache_creation_input_tokens` + `cache_read_input_tokens` |
| `out` | inteiro | `output_tokens` |
| `usd` | número | Já calculado, com as quatro tarifas do modelo que rodou |

**`in` é a soma de todos os contadores do lado de entrada.** É volume lido, não custo. O custo está
em `usd` e só nele.

**JSON por linha, e não tabela pipe, por dois motivos:** parsing imune a qualquer caractere nos
campos, e extensibilidade — acrescentar `"c_rd"` ou `"modelo"` nas linhas novas não quebra o leitor,
e as linhas antigas seguem válidas. A decisão de gravar só dois contadores é, por isso, reversível
sem migração.

**O que a linha não tem:** total. `TOTAL` é sempre calculado na leitura, nunca gravado.

---

## 4. Escrita

```
POST /api/cards/{issueKey}/custos
{ "modelo": "claude-opus-5",
  "inputTokens": 139, "cacheCreationTokens": 372804,
  "cacheReadTokens": 8206013, "outputTokens": 58743 }
  ->  201 com a linha gravada
```

Sequência:

1. Resolve o pai do `issueKey` lendo `fields.parent`. **O `JiraIssueResponseFields` ainda não lê esse
   campo** — acrescentar. Sem pai, o alvo é o próprio card.
2. Calcula o USD com as quatro tarifas do `modelo`.
3. Lê a descrição do card alvo, faz o splice entre as sentinelas, grava de volta.
4. Adquire um **lock por `issueKey` do card alvo** durante os passos 3 e 4.

### Concorrência — a condição de corretude

Escrita é read-modify-write, e o Jira **não oferece compare-and-swap** em update de issue. Duas
subtarefas terminando ao mesmo tempo podem perder uma linha.

O lock por `issueKey` fecha a janela **dentro de um processo**. Portanto:

> **O serviço `jira_master` deve rodar em instância única.** Duas instâncias e a janela reabre, com
> perda de linha silenciosa — sem erro, sem log, sem sintoma.

Mitigação parcial de graça: o serviço faz bind numa porta fixa, então uma segunda instância **no
mesmo host** não sobe. A janela só reabre com instâncias em hosts diferentes. Se um dia isso for
necessário, esta decisão precisa ser revista antes.

---

## 5. Leitura

`GET /api/cards/{issueKey}` ganha dois campos, somados do bloco no momento da leitura:

```json
{ "issueKey": "KAN-5", ...,
  "custoTotal": 2.29,
  "custos": [ {"ts": "...", "card": "KAN-15", "in": 12000, "out": 4200, "usd": 0.24}, ... ] }
```

**Linha malformada é ignorada e contada, nunca derruba a resposta.** Um caractere torto num card não
pode cegar o custo da árvore inteira. A contagem de linhas descartadas vai na resposta, para quem lê
saber o quanto confiar no total — mesmo princípio do `filhosSemCusto` que o `KAN-18` já previa.

Isto entrega `KAN-18` quase de graça: o total da árvore é a soma do bloco do pai, uma chamada.

---

## 6. O que esta decisão supersede

Duas regras do documento do The Eye ficam **desatualizadas** e precisam de nova versão do documento.
Enquanto não forem reescritas, são regra morta contradizendo o código.

| Seção | Dizia | Passa a valer |
|---|---|---|
| `§3.8` | "O total é calculado na leitura, nunca armazenado no pai. Cada subtarefa escreve só o próprio custo, no próprio card" | O filho grava sua linha no bloco do pai. O **total** continua nunca sendo gravado — só as linhas. A proteção contra sobrescrita passa a ser o lock + instância única, não a separação de campos |
| `§11.6` | Custo em **comentário-ledger**, marcado como estimativa | Custo em bloco na **descrição**, valor medido e não estimado. Comentário segue válido para narrativa de fechamento, não para o número |

A `§3.8` não estava errada — o risco que ela descreve é real e foi medido nesta discussão. A decisão
foi **aceitar esse risco** em troca de leitura em uma chamada e tabela visível na tela do Jira,
com instância única como condição.

---

## 7. Custom field numérico — por que ficou fora

O `TODO-extensoes` (linha 129) apontava o campo customizado como argumento forte: soma nativa,
consulta por JQL, coluna no board, zero parsing. Continua verdade, e continua sendo a alternativa
óbvia se esta decisão for revista.

Ficou fora por duas razões concretas:

1. **Exige admin do Jira** para criar o campo — dependência externa que trava a implementação.
2. **É um escalar.** Guarda o total, não o detalhamento por execução. O sinal de retrabalho — duas
   linhas do mesmo card — não cabe num número, então o bloco seria necessário de qualquer forma e o
   campo viraria cache com regra de reconciliação.

Se um dia o campo for criado, ele deve ser **derivado** do bloco, nunca fonte da verdade.

---

## 7-A. Adendo de 2026-08-08 — o que a implementação mudou

Três ajustes decididos durante a implementação de `KAN-17`, `KAN-18`, `KAN-19` e `KAN-38`, todos
verificados contra o Jira real.

**`ts` é gravado em UTC.** A spec dizia "sem timezone"; sem fuso declarado a linha ficaria ambígua.
O Jira devolve tudo em UTC e misturar fusos num mesmo bloco seria pior. UTC, minuto, sem sufixo.

**O contrato do `KAN-18` mudou de forma, não de conteúdo.** Foi especificado como "somar o custo de
cada filho", pressupondo um bloco por card. Com o ledger no pai, todas as linhas estão num bloco só:
o agregado sai de **uma chamada**, agrupando por `card`. Mesma resposta (`custoProprio`, por card,
total, `filhosSemCusto`), sem N+1.

**`custoTotal` nulo e `custoTotal` zero são respostas diferentes, e agora em toda a API.** Nulo é
"nunca medido"; zero é "medido e as linhas se anulam", que passou a ser possível com o estorno. A
primeira versão devolvia `0` no agregado quando não havia linha nenhuma — corrigido, porque é
exatamente o erro que a seção 5 desta spec proíbe.

**Estorno resolve a lacuna que a spec não previa.** Não havia caminho para corrigir linha errada, e
o ledger é append-only de propósito. `POST /custos/estornos` acrescenta uma linha com os valores
negativos, apontando a corrigida em `estorna`. Nada é apagado: o total volta ao certo e a auditoria
continua mostrando que houve erro e correção. Estorno em duplicidade é recusado.

---

## 8. Fora de escopo

- Ledger em comentário (superseded pela §11.6 acima)
- `GET` de comentários — o controller só tem `POST /comentarios`, e nada nesta decisão precisa de
  leitura de comentário
- Total gravado em campo do Jira
- Rateio automático de uma sessão entre vários cards — ver "limitação conhecida" abaixo

---

## 9. Limitação conhecida

Uma sessão de Claude Code frequentemente toca **vários cards**. O transcript dá o custo da sessão
inteira, não por card. Não existe forma automática de ratear.

A regra: **o agente atribui**, e atribui de forma honesta. Sessão que trabalhou majoritariamente num
card registra nele. Sessão genuinamente dividida registra linhas separadas com a estimativa de
divisão, e diz na narrativa que foi rateio estimado. Inventar precisão que não existe é pior do que
declarar a imprecisão.

---

## 10. Critérios de aceite

1. `POST /api/cards/KAN-15/custos` com os quatro contadores grava uma linha no bloco do **pai** de
   `KAN-15`, e retorna `201`.
2. O texto humano da descrição do pai, fora das sentinelas, sobrevive intacto à escrita.
3. Duas escritas seguidas geram duas linhas — nenhuma some.
4. `GET /api/cards/{pai}` devolve `custoTotal` igual à soma das linhas, sem que o total tenha sido
   gravado em lugar nenhum.
5. Uma linha corrompida à mão dentro do bloco é ignorada, contada na resposta, e não impede o cálculo
   do resto.
6. Card sem bloco devolve `custoTotal` ausente ou nulo — **não** `0`. Zero e "não medido" não são a
   mesma coisa, e mentir para baixo no número que autoriza gasto é o pior erro possível aqui.
7. O bloco renderiza na tela do Jira como bloco de código com uma linha por execução, legível.
