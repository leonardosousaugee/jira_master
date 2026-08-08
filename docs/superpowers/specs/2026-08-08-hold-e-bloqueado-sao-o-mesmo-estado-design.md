# Spec — HOLD e BLOQUEADO são o mesmo estado

**Card:** `KAN-24` — "Definir a mecânica de BLOQUEADO e HOLD no fluxo do KAN" (questão pequena da
§7.2, sob `KAN-7`).
**Decidido em:** 2026-08-08, por decisão direta do responsável pelo projeto.
**Define a semântica de:** `KAN-19` (mover a árvore inteira para HOLD).

---

## Decisão

**`BLOQUEADO` e `HOLD` são o mesmo estado.** Não existem dois conceitos, um mecanismo, um vocabulário.

---

## O que a medição mostrou

A pergunta do card pressupunha dois estados a conciliar. O fluxo do board não tem dois. Listando as
transições de qualquer card do `KAN`:

| id | etapa |
|---|---|
| 11 | A fazer |
| 21 | Em andamento |
| 31 | Em análise |
| **41** | **HOLD** |
| 51 | Concluído |

Não existe etapa `BLOQUEADO`. Ela aparecia só na prosa do documento, nunca no board. Então a decisão
não arbitra entre duas opções concorrentes — reconhece que só uma sempre existiu, e elimina um
vocabulário paralelo que não tinha lastro.

## Por que unificar em vez de criar o segundo estado

A alternativa seria criar `BLOQUEADO` como etapa distinta, com semântica própria (por exemplo:
"HOLD é pausa deliberada, BLOQUEADO é impedimento externo"). Foi descartada:

- **Dois estados exigem uma regra de precedência** para o kill switch: se o pai está em HOLD e o
  filho em BLOQUEADO, o que a árvore está? Toda resposta a essa pergunta é convenção arbitrária que
  alguém vai esquecer na hora ruim.
- **A distinção é motivo, não estado.** Por que parou cabe no comentário do card, que é onde motivo
  já mora. Promover motivo a estado duplica a máquina de estados sem ganhar controle.
- **O kill switch tem que ser simples.** `KAN-19` existe para funcionar quando tudo mais está pegando
  fogo. Um estado, uma transição, uma pergunta binária.

## Como o serviço implementa

`POST /api/cards/{issueKey}/hold` move a árvore. Ao procurar a transição, o serviço aceita como
sinônimos, sem diferenciar maiúsculas:

```
HOLD   BLOQUEADO   BLOCKED   BLOCK
```

O board do `KAN` só oferece `HOLD`, então na prática só ele casa. Os outros nomes existem para o
serviço funcionar num board que chame a mesma etapa de outro jeito — não para reintroduzir a
distinção pela porta dos fundos. **Todos significam a mesma coisa e são tratados igual.**

## Consequências para os documentos

| Onde | Passa a valer |
|---|---|
| `§3.9` (kill switch) | Onde disser "BLOQUEADO e HOLD", ler "HOLD". Não há dois estados a coordenar |
| `docs/protocolo-de-controle-de-tarefas.md` | Seção de HOLD atualizada com a equivalência e o endpoint |
| `TODO-extensoes-subtarefas-custo-e-hold.md` | O `KAN-19` já assumia HOLD como etapa nativa; a decisão confirma e remove a ambiguidade do nome |

## Critérios de aceite

1. Uma chamada move pai e filhos para HOLD, **pai primeiro**. Verificado contra o Jira real com
   `KAN-39` e duas filhas.
2. Repetir a chamada devolve sucesso com `"ja estava em HOLD"`, sem transicionar de novo.
3. Um board que exponha a etapa como `BLOQUEADO` é atendido pelo mesmo endpoint, sem configuração.
4. Filho que não pode mover aparece na resposta com o motivo, e os demais movem assim mesmo.
