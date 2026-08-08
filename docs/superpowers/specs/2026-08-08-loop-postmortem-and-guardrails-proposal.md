# Postmortem do loop no piloto + proposta de guardrails

**Data:** 2026-08-08
**Autor:** Leonardo + Claude
**Status:** Frente 2 — fechada (diagnóstico). Frente 3 — proposta entregue, **nada implementado ainda**.
**Escopo:** este documento cobre só entender o que aconteceu e propor como evitar que aconteça de
novo. Não é um plano de implementação — quando algum item da proposta for para código, ele vira
seu próprio spec → plano → implementação, como todas as outras frentes.

## Frente 2 — Diagnóstico: por que o piloto entrou em loop

**Contexto.** Num projeto anterior a este, Leonardo montou um time de agentes autônomos e
conversacionais que colaboravam entre si sobre um quadro kanban (Postgres). Queria que os
agentes pudessem se chamar. Funcionou por um tempo — depois entrou em loop, e o que interrompeu
foi o **limite de gastos da conta Anthropic**, não nenhuma salvaguarda de design. O projeto em
si não sobrou (sem logs de runtime); o que sobrou foi o template extraído dele,
`C:\pessoal\company_template`, com as mesmas regras de comportamento que estavam em vigor
naquele momento.

**Causa raiz confirmada, com evidência textual do template:**

1. **`general-work-premises/02-base-agent-behavior.md`** estabelece a regra: *"The agent
   doesn't talk to another agent directly... create a new card in 'todo' assigned to the other
   agent"* — nenhuma chamada direta entre agentes, o card é o único canal.

2. **`general-work-premises/03-called-to-converse.md`** reabre exatamente esse canal para
   conversas ao vivo: *"the owner **or another agent** invokes it directly in a session"*. E
   define o único freio que existe: *"Talking is free; executing without having gone through
   the kanban board isn't."* — ou seja, **conversar** entre agentes nunca teve limite nenhum;
   só **executar** sem card era proibido.

3. **`workers/explorer/TRAINING.md`** transforma essa brecha em prática concreta: ao ser
   consultado, Explorer deve *"actually go get the opinion... through an agent tool"* de outro
   worker, ao vivo, podendo consultar **mais de um** especialista por pergunta ("it's not
   stalling, it's error reduction"). Nada no arquivo define profundidade máxima, nem impede que
   o worker consultado consulte de volta quem o chamou.

4. **Toda a documentação repete a mesma isenção** — "conversa rápida não precisa de card" — em
   `ai-expert/TRAINING.md`, `explorer/TRAINING.md`, `orchestrator/TRAINING.md` e
   `_template-specialist/TRAINING.md`. Isso significa que a atividade que efetivamente descontrolou
   (conversa ao vivo entre agentes) é, pela própria definição do sistema, **invisível no quadro**
   — que é a única superfície de auditoria que o template define
   (`01-kanban-manual.md`: *"there is no parallel channel... the kanban board is the only
   source of work"*). O board dizia que nada estava acontecendo enquanto a conversa entre
   agentes rodava por fora dele.

5. Existe ainda um caminho documentado de **Orchestrator ↔ Explorer ↔ ai-expert** se chamando
   entre si ao vivo ("suggests calling ai-expert", "flags... and suggests ai-expert"), sem card e
   sem qualquer marca de "já fui chamado nesta cadeia".

**Conclusão do diagnóstico:** não foi um bug de código — foi uma regra de design (premissa 03)
que reabriu, de propósito, o canal que a premissa 02 fechava, sem colocar limite nenhum nele. O
sistema tinha exatamente um mecanismo de controle (o quadro) e a atividade que descontrolou
estava, por definição, fora dele.

## Frente 3 — Proposta: como não repetir esse erro

Proposta, não implementação. Cada item pode virar seu próprio spec quando for a vez.

1. **Profundidade máxima de conversa entre agentes.** Toda vez que um agente chama outro ao
   vivo, propaga um contador (`depth`) junto com a chamada. Acima de um teto (ex. 3), o agente
   chamado é obrigado a responder "preciso de um card para continuar" em vez de encadear mais
   uma consulta — fecha exatamente a brecha do item 3 do diagnóstico.

2. **Detecção de ciclo.** Junto com `depth`, propagar a lista de quem já está na cadeia da
   conversa atual (`visited = [orchestrator, explorer]`). Se um agente for chamado por alguém
   que já está nessa lista, ele recusa e sinaliza — fecha a brecha do item 5.

3. **Orçamento por conversa, não só por conta.** Um teto de tokens/turnos por cadeia de
   consulta ao vivo (não por dia, não por conta inteira) — se estourar, a cadeia é interrompida
   e o que sobrou de trabalho vira um card, com o motivo da interrupção anotado nele. Isso troca
   o limite de gastos da conta (rede de segurança por acidente) por um circuit breaker de
   design, escopado à própria atividade que pode descontrolar.

4. **"Conversa rápida" deixa de ser automaticamente invisível.** Hoje qualquer conversa entre
   agentes é isenta do quadro. Proposta: a isenção continua valendo para conversa **owner ↔
   agente**, mas conversa **agente ↔ agente** acima de 1 hop de profundidade passa a exigir um
   card mínimo (mesmo que só de acompanhamento) — o quadro volta a ser a superfície de auditoria
   real para o que efetivamente pode descontrolar.

5. **O card do incidente, quando acontecer, aponta a régua usada.** Se um circuit breaker
   disparar, o card gerado registra `depth` atingido, quem estava na cadeia, e quantos
   tokens/turnos foram consumidos — para o post-mortem de um futuro incidente ser imediato, não
   uma investigação de arquivo por arquivo como esta foi.

**Fora desta proposta (fica para quando alguma frente virar spec de implementação):** onde
exatamente esse contador/lista viajam (header de request? campo na mensagem? estado do
orquestrador?), qual o valor certo de teto de profundidade e de orçamento, e como isso se
integra com `jira-master-service` como o board real. São decisões de design que merecem seu
próprio ciclo — aqui só a proposta de **o que** guardar, não **como**.
