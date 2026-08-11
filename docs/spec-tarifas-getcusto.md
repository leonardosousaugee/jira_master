# SPEC — tabela de tarifas como serviço no `jira_master` (`getCusto`)

**Versão:** v1 — 2026-08-10
**Card:** — (a abrir no `jira_master`)
**Status:** pedido de implementação. **Nada aqui está no ar.**

> **Este arquivo não é regra para quem lê o `writerHand`.** Não é treinamento, não é premissa, e
> nenhum agente deve segui-lo como instrução de trabalho. É a especificação de um endpoint a ser
> construído no `jira_master`, encostada aqui porque é onde ela nasceu.
>
> Pela regra de leitor único (§14 do documento mestre), **o lugar definitivo dela é o repositório do
> `jira_master`**. Deve migrar para lá junto com o card de implementação.

---

## 1. Problema

A tabela de tarifas em dólar está escrita em texto, em pelo menos três lugares, e nenhum deles é
consultável por máquina:

| Onde | O que tem | Quem lê |
|---|---|---|
| §11.6 do documento mestre | Tabela completa, fórmula, multiplicadores de cache | Leonardo |
| Memória da sessão de orquestração do The Eye | Cópia condensada da mesma tabela | A sessão que conduz o projeto |
| `premise.md` de cada role | **Nada.** Só a frase "o serviço aplica a tabela de tarifas" | Cada worker |

Consequências já observadas:

- **O worker não consegue estimar custo** — ele não tem a tabela, e por desenho o papel dele é
  mandar contadores de token, não dólares.
- **A disciplina de custo vive numa memória de sessão.** Perdida a memória, ninguém faz a conta:
  nenhum `premise.md` pede.
- **Três cópias de texto divergem em silêncio.** Preço muda e ninguém sabe qual cópia está velha.

## 2. O que se pede

Um endpoint de **cálculo puro** no `jira_master`: recebe modelo e contadores de token, devolve custo
em dólar. Sem efeito colateral, sem gravar nada.

A partir dele, a tabela de tarifas passa a existir **num lugar só, em código**, e todos os outros
lugares passam a apontar para ele em vez de repetir números.

### 2.1 Forma sugerida

```
GET /api/tarifas/custo
    ?modelo=claude-opus-5
    &entradaNova=120000
    &entradaCacheLida=850000
    &entradaCacheEscrita5m=0
    &entradaCacheEscrita1h=40000
    &saida=6000
```

Resposta:

```json
{
  "modelo": "claude-opus-5",
  "custoUsd": 1.2345,
  "detalhe": {
    "entradaNova": 0.60,
    "entradaCacheLida": 0.425,
    "entradaCacheEscrita5m": 0.0,
    "entradaCacheEscrita1h": 0.40,
    "saida": 0.15
  },
  "tabelaVersao": "2026-06-24"
}
```

**`tabelaVersao` não é enfeite.** Sem ela, uma mudança de preço reescreve retroativamente o
significado de todo número já calculado. É o mesmo raciocínio do hash de treinamento (§3.3.1): o
resultado tem que dizer com que insumo foi produzido.

### 2.2 Cinco contadores, não dois

A fórmula do §11.6 tem componentes com multiplicadores diferentes. Somar tudo como "entrada" infla o
número em quase dez vezes numa sessão longa, onde a maior parte da entrada é leitura de cache.

| Contador | Multiplicador sobre o preço de entrada |
|---|---|
| `entradaNova` | 1× |
| `entradaCacheLida` | ≈ 0,1× |
| `entradaCacheEscrita5m` | 1,25× |
| `entradaCacheEscrita1h` | 2× |
| `saida` | preço de saída (≈ 5× o de entrada) |

Contador ausente vale zero. **Todos ausentes não vale zero** — vale erro, senão o endpoint devolve
`custoUsd: 0` para quem não mandou nada, e zero medido é diferente de não medido (§3.8).

### 2.3 Tabela a embutir

Os números **não são repetidos aqui**. Esta seção pedia uma tabela de preços em texto, e escrever a
tabela num documento é exatamente o problema que o endpoint veio resolver: uma segunda cópia que
envelhece calada e ninguém sabe qual das duas está certa.

Onde os preços moram, desde que o endpoint subiu:

| Camada | O que tem |
|---|---|
| `src/main/resources/application.yml`, sob `custo.tarifas` | os números, uma linha por modelo, em dólares por milhão de tokens — **fonte única** |
| `custo.tabela-versao`, no mesmo arquivo | a data de referência da tabela |
| `GET /api/tarifas/custo` | a leitura; devolve o dólar calculado e a `tabelaVersao` que produziu o número |
| `README.md`, seção "Tarifas — a tabela como serviço" | como ler e como usar, sem valor nenhum em dólar |

A data de referência sai na resposta como `tabelaVersao` — sem ela, uma mudança de preço reescreve
retroativamente o significado de todo número já calculado.

## 3. Regras que o endpoint tem que respeitar

| Regra | Motivo |
|---|---|
| **Uma tabela, dois chamadores.** O mesmo objeto de tarifas serve `getCusto` e o `POST /api/cards/{key}/custos` que já existe | Duas tabelas em código é o problema atual com passos extras |
| **Modelo desconhecido é recusado, nunca calculado com tarifa padrão** | "Gravar custo errado é pior do que recusar a gravar" (§11.6). Vale igual para calcular |
| **Cálculo puro: não grava, não move card, não escreve log de custo** | Registrar é o outro endpoint. Misturar os dois faz consulta virar escrita acidental |
| **O caminho de registro não depende deste endpoint** | `POST /custos` continua calculando por dentro. Assim `getCusto` fora do ar não impede trabalho de ser registrado |

## 4. Questão aberta — variante de modelo

O registro de custo hoje aceita só `claude-opus-5`, `claude-sonnet-5`, `claude-haiku-4-5`, e
**sufixo de variante devolve `422`** (documentado no `premise.md`). Mas existem variantes em uso real
— por exemplo contexto de 1M — e **não está confirmado se elas têm o mesmo preço da variante base**.

Duas saídas, e a escolha é de quem implementa, não deste arquivo:

1. **Recusar variante** (comportamento atual). Honesto, e força a pergunta a ser respondida antes de
   qualquer número ser gravado.
2. **Aceitar variante com tarifa própria**, uma linha por variante na tabela.

**O que não serve: aceitar a variante e cobrar o preço da base.** Isso grava número errado com cara
de certo, que é o pior resultado possível para um ledger.

Confirmar na página de preços da Anthropic antes de decidir.

## 5. Contrapartida a saber

Hoje a tabela é texto num documento: sempre disponível, nunca fora do ar. Virar endpoint troca **três
cópias que divergem** por **uma cópia que pode estar indisponível**.

A troca vale, e a regra do §3 acima é o que a torna barata: como o registro de custo não depende do
`getCusto`, o serviço fora do ar atrapalha consulta e estimativa, nunca a gravação do ledger.

## 6. O que sai dos arquivos quando o endpoint subir

Ordem importa: **primeiro o endpoint responde, depois os textos perdem os números.** Retirar antes
deixa o sistema sem tabela nenhuma.

| Onde | O que sai | O que entra | Como |
|---|---|---|---|
| §11.6 do documento mestre | Tabela de preços e multiplicadores de cache | Ponteiro para o endpoint, mais a fórmula (que é conceito, não preço) | **Versão nova do documento** — §11.4 proíbe editar em cima |
| Memória da sessão de orquestração | Tabela condensada | "Consultar `GET /api/tarifas/custo`" | Reescrita da memória |
| `premise.md` de cada role | Nada — não tem tabela | Referência ao endpoint na seção de custo | Uma versão nova **por role**, porque o `premise` está duplicado hoje (§3.3.4) |
| Este arquivo | — | — | Vira histórico; migra para o repositório do `jira_master` |

**Fica de propósito no §11.6:** a fórmula, o fato de que saída custa ~5× a entrada, e a análise do
piloto (razão entrada/saída de 6.000 para 1). Isso é raciocínio, não tarifa — não caduca com mudança
de preço.

## 7. Como saber que funcionou

| Teste | Esperado |
|---|---|
| Modelo válido, cinco contadores | Total em dólar, detalhe por componente, `tabelaVersao` presente |
| Só `entradaCacheLida` alta | Custo ~10× menor que a mesma quantidade em `entradaNova` |
| Modelo inexistente | Erro explícito, nunca total calculado |
| Nenhum contador | Erro, não `custoUsd: 0` |
| Mesmos contadores em `getCusto` e em `POST /custos` | **Mesmo número.** Divergência aqui significa duas tabelas em código |

O último teste é o que prova que o problema foi resolvido. Os outros provam que o endpoint funciona.
