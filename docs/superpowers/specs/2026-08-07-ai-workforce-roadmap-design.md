# Design — AI Workforce Roadmap (tracker site)

**Data:** 2026-08-07
**Autor:** Leonardo + Claude (brainstorming)
**Status:** aprovado, pronto para implementação

## Contexto

Leonardo está construindo uma iniciativa maior do que o `jira-master-service`: um "quadro de
operação" onde ele conversa com Claude, cards nascem no Jira, e agentes autônomos/conversacionais
vão executando o trabalho — um modelo de força de trabalho de IA que complementa (e paga melhor)
a força de trabalho humana, ao liberar as pessoas para qualidade em vez de execução repetitiva.

Já existe um piloto real e validado num projeto anterior, do qual nasceu o template
`C:\pessoal\company_template` (kanban em Postgres, `general-work-premises/`, workers
`orchestrator`/`explorer`/`ai-expert`). Esse piloto funcionou, mas um mecanismo de consulta
ao vivo entre agentes (`explorer` consultando outro worker "through an agent tool", sem limite
de profundidade nem detecção de ciclo — ver `general-work-premises/03-called-to-converse.md`)
aparentemente entrou em loop; o que interrompeu foi o limite de gastos da conta Anthropic, não
uma salvaguarda de design.

Este spec cobre **apenas o primeiro entregável**: um site (artifact HTML) que documenta a
visão completa em 9 frentes, servindo como roadmap vivo — Leonardo acompanha o progresso ali e
nesta própria conversa. Cada frente além da primeira vira, futuramente, seu próprio ciclo de
spec → plano → implementação; este documento não projeta a solução de nenhuma delas, só a
página que as expõe.

## As 9 frentes

| # | Frente | Status nesta data |
|---|--------|--------------------|
| 1 | Microsserviço Jira como orquestrador de tickets (`jira-master-service`) | **Quase lá** — construído e smoke-testado; falta anexar, na descrição do card ao concluir, o custo estimado (tokens de entrada/saída e modelos usados no processo) |
| 2 | Entender a falha do piloto: consulta ao vivo entre agentes sem limite de profundidade/ciclo, só interrompida pelo limite de gastos | Planejado |
| 3 | Guardrails: limite de profundidade de chamada, detecção de ciclo, orçamento por tarefa, circuit breaker real (não o limite de conta como rede de segurança) | Planejado |
| 4 | Padrões por agente: `TRAINING.md`/`CLAUDE.md` de comportamento, toda tarefa entra com spec, contexto zerado a cada rodada | Planejado |
| 5 | Catálogo de ambientes: o agente sabe em qual máquina/ambiente está rodando (estudo vs. corporativo) | Planejado |
| 6 | Catálogo de serviços: ações sensíveis (ex. commit em branch) executadas por um microsserviço determinístico, nunca por chamada direta agente-a-agente | Planejado |
| 7 | Entregável claro: descrito na spec de entrada e reafirmado, pelo próprio agente, na descrição do card que ele cria | Planejado |
| 8 | `espolios.md` opcional ao fim de cada rodada — agente sugere linhas para o próprio `TRAINING.md`/`CLAUDE.md` | Em aberto (pode ou não existir) |
| 9 | Registro de agentes + portabilidade total (outro colaborador, ou o PC corporativo do Leonardo) | Planejado |

Mapeamento de status → coluna do quadro na página: **Concluído** (nenhuma frente ainda),
**Quase lá** (frente 1), **Planejado** (frentes 2, 3, 4, 5, 6, 7, 9), **Em aberto** (frente 8).

## Formato da página

Artifact HTML único (`.claude` scratchpad → publicado), **tema escuro fixo** (preferência
permanente do usuário — ver memória `feedback_dark_theme_pages`), com efeitos JavaScript
deliberados — tratamento editorial, não utilitário. Referência de mood pedida pelo usuário:
`hubble.com`, ajustada com o feedback direto dele: **mais escuro, sem tons de roxo**.

### Paleta

| Token | Valor | Uso |
|---|---|---|
| `--bg` | `#070909` | fundo da página, quase preto neutro |
| `--surface` | `#0f1315` | cards, painéis |
| `--surface-2` | `#171c1f` | hover/realce sutil |
| `--ink` | `#eef1f0` | texto principal |
| `--ink-soft` | `#93a0a2` | texto secundário |
| `--ink-faint` | `#5c6668` | metadados, legendas |
| `--border` | `#20262a` | divisórias |
| `--accent` | `#ff9d2e` | âmbar — atenção / "em progresso"; dobra como cor da coluna Quase lá |
| `--status-done` | `#35d0a0` | teal — Concluído |
| `--status-planned` | `#6c7d86` | slate neutro — Planejado |
| `--status-open` | `#4f8fa0` | azul-acinzentado dessaturado — Em aberto (borda tracejada, sem roxo) |

### Tipografia

- Display/headings e labels de status: pilha `ui-monospace` — estética de "readout" de
  terminal/console de operação, coerente com o tema de agentes autônomos.
- Corpo (descrição de cada frente): sans humanista (`ui-sans-serif`/`system-ui`) para leitura
  confortável.
- Números de fase e contadores: `font-variant-numeric: tabular-nums`.

### Layout

1. **Hero** full-bleed, altura ~60vh. Título + subtítulo curto (a tese do projeto em uma frase).
   Canvas ambiente ao fundo: nós conectados por linhas finas, pulsos ocasionais viajando entre
   nós (metáfora de cards fluindo entre agentes) — decorativo, contido ao hero, para em
   `prefers-reduced-motion`. Um spotlight radial acompanha o cursor sobre o hero (também
   desativado em `prefers-reduced-motion`).
2. **Faixa de status**: contadores rápidos (ex. "9 frentes · 1 quase lá · 7 planejadas ·
   1 em aberto · atualizado em 07/08/2026").
3. **Quadro kanban**: 4 colunas (Concluído / Quase lá / Planejado / Em aberto), cada frente é
   um card com número, título, uma linha de resumo e a etiqueta de status. Clique expande um
   painel inline com o detalhe completo (a descrição da tabela acima). Em telas estreitas as
   colunas empilham verticalmente, mantendo a ordem.
4. **Nota de rodapé**: o documento é vivo — mesmo link, atualizado conforme cada frente avança;
   aponta que o acompanhamento também continua nesta conversa.

### Fora de escopo deste spec

- O desenho de qualquer uma das 9 frentes em si (guardrails, catálogo de ambientes, etc.) —
  cada uma recebe seu próprio spec quando for a vez dela.
- Qualquer alteração no `jira-master-service` ou no `company_template`.
- Autenticação, backend, ou persistência — a página é estática; atualizações futuras são feitas
  editando o arquivo e republicando no mesmo artifact.

## Critério de pronto

- Página publicada como artifact, tema escuro, sem paleta roxa.
- As 9 frentes presentes, cada uma na coluna correta, com o resumo da tabela acima.
- Efeitos (canvas ambiente + spotlight) funcionam e respeitam `prefers-reduced-motion`.
- Responsiva: colunas empilham em telas estreitas sem scroll horizontal na página.
