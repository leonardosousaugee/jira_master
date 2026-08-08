# AI Workforce Roadmap (tracker site) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and publish a single self-contained HTML artifact — a dark-themed, kanban-styled tracker page for the 9-front "AI workforce" roadmap described in `docs/superpowers/specs/2026-08-07-ai-workforce-roadmap-design.md`.

**Architecture:** One HTML file (inline `<style>` + inline `<script>`, no external requests — CSP-safe) built in layers: static structure and design tokens first, then click-to-expand interaction, then the ambient canvas/spotlight motion layer on top. Published via the `Artifact` tool, which handles the `<!doctype>`/`<html>`/`<body>` wrapper.

**Tech Stack:** Plain HTML5, CSS (custom properties, no framework), vanilla JS (Canvas 2D API for the ambient effect). No build step, no dependencies.

## Global Constraints

- Dark theme is the fixed default look — no light-mode-first design (per `feedback_dark_theme_pages` memory). A `data-theme="light"` override may still exist per the artifact contract, but is not the point of this page.
- No purple/violet anywhere in the palette (explicit user correction during brainstorming).
- Background darker than the initial proposal: `--bg: #070909`.
- All motion (ambient canvas, cursor spotlight) must stop under `prefers-reduced-motion: reduce`.
- No horizontal scroll on the page body; wide content is not expected here, but columns must stack on narrow viewports without overflow.
- Single file, no external font/script/style URLs (Artifact CSP blocks them).
- File lives at `C:\Users\usuario\AppData\Local\Temp\claude\C--Projetos-jira-master\8d205548-0e3d-4993-81f2-fa65771854a6\scratchpad\ai-workforce-roadmap.html` (scratchpad — this file is NOT part of the git repo and is never `git commit`ed; "commit" for this artifact means publish/update via the `Artifact` tool).

---

### Task 1: Design tokens, page skeleton, and static kanban board

**Files:**
- Create: `C:\Users\usuario\AppData\Local\Temp\claude\C--Projetos-jira-master\8d205548-0e3d-4993-81f2-fa65771854a6\scratchpad\ai-workforce-roadmap.html`

**Interfaces:**
- Produces: the CSS custom properties (`--bg`, `--surface`, `--surface-2`, `--ink`, `--ink-soft`, `--ink-faint`, `--border`, `--accent`, `--status-done`, `--status-planned`, `--status-open`), the `.hero` section (with an empty `<canvas id="hero-canvas">` placeholder Task 3 fills), the `.board` grid with 4 `.column` elements (`data-status="done|almost|planned|open"`), and one `.card` per front with `data-detail` holding the full description text and a `.card-detail` sibling element (initially `hidden`) — these hooks are what Task 2's JS and Task 3's canvas selector depend on.

- [ ] **Step 1: Write the design tokens and base layout CSS**

```css
:root {
  --bg: #070909;
  --surface: #0f1315;
  --surface-2: #171c1f;
  --ink: #eef1f0;
  --ink-soft: #93a0a2;
  --ink-faint: #5c6668;
  --border: #20262a;
  --accent: #ff9d2e;
  --accent-ink: #ffcf94;
  --status-done: #35d0a0;
  --status-planned: #6c7d86;
  --status-open: #4f8fa0;
  --font-display: ui-monospace, "Cascadia Code", "SF Mono", Consolas, monospace;
  --font-body: ui-sans-serif, "Segoe UI", system-ui, sans-serif;
}

* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
@media (prefers-reduced-motion: reduce) { html { scroll-behavior: auto; } }

body {
  margin: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-body);
  line-height: 1.6;
}
```

- [ ] **Step 2: Write the hero section markup and CSS**

```html
<header class="hero">
  <canvas id="hero-canvas" aria-hidden="true"></canvas>
  <div class="hero-spotlight" aria-hidden="true"></div>
  <div class="hero-content">
    <span class="hero-eyebrow">Plano de operação — força de trabalho de IA</span>
    <h1>Do proxy de tickets ao workforce autônomo</h1>
    <p class="hero-tagline">Nove frentes, uma de cada vez, cada uma com seu próprio ciclo de spec → plano → implementação.</p>
    <div class="status-strip" id="status-strip"><!-- filled by Step 4 counts, written statically --></div>
  </div>
</header>
```

```css
.hero {
  position: relative;
  min-height: 60vh;
  display: flex;
  align-items: center;
  overflow: hidden;
  border-bottom: 1px solid var(--border);
}
.hero-canvas { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-spotlight {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(circle at 50% 50%, rgba(255,157,46,0.08), transparent 45%);
}
.hero-content { position: relative; z-index: 1; max-width: 700px; padding: 0 2.5rem; }
.hero-eyebrow {
  font-family: var(--font-display); font-size: 0.75rem; letter-spacing: 0.1em;
  text-transform: uppercase; color: var(--accent-ink);
}
.hero h1 { font-family: var(--font-display); font-size: 2.1rem; line-height: 1.2; text-wrap: balance; margin: 0.6rem 0; }
.hero-tagline { color: var(--ink-soft); max-width: 46ch; }
.status-strip { font-family: var(--font-display); font-size: 0.8rem; color: var(--ink-faint); margin-top: 1rem; }
```

- [ ] **Step 3: Write the kanban board markup for all 9 fronts**

Content for each card comes verbatim from the spec's front table. Structure (repeat the `.card` pattern per front, grouped into 4 `.column` elements titled Concluído / Quase lá / Planejado / Em aberto):

```html
<main class="board" aria-label="Quadro das 9 frentes">
  <section class="column" data-status="done">
    <h2>Concluído <span class="count">0</span></h2>
  </section>

  <section class="column" data-status="almost">
    <h2>Quase lá <span class="count">1</span></h2>
    <article class="card" data-status="almost" tabindex="0">
      <span class="card-num">01</span>
      <h3>Microsserviço Jira como orquestrador</h3>
      <p class="card-summary">Construído e smoke-testado contra o Jira real.</p>
      <span class="card-tag">quase lá</span>
      <div class="card-detail" hidden>
        Falta anexar, na descrição do card ao concluir, o custo estimado (tokens de entrada/saída
        e modelos usados no processo) daquele card.
      </div>
    </article>
  </section>

  <section class="column" data-status="planned">
    <h2>Planejado <span class="count">7</span></h2>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">02</span>
      <h3>Entender a falha do piloto</h3>
      <p class="card-summary">Consulta ao vivo entre agentes sem limite de profundidade nem detecção de ciclo.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        No piloto anterior (origem do template company_template), o worker <code>explorer</code>
        consultava outros workers ao vivo ("through an agent tool") sem limite de profundidade
        ou detecção de ciclo. O que interrompeu o loop foi o limite de gastos da conta Anthropic,
        não uma salvaguarda de design.
      </div>
    </article>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">03</span>
      <h3>Guardrails</h3>
      <p class="card-summary">Limite de profundidade, detecção de ciclo, orçamento por tarefa, circuit breaker real.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        Substituir o limite de gastos da conta (rede de segurança acidental) por salvaguardas de
        design: profundidade máxima de chamada entre agentes, detecção de ciclo (A chama B chama A),
        orçamento de tokens por tarefa, e um circuit breaker que interrompe antes do limite de conta.
      </div>
    </article>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">04</span>
      <h3>Padrões por agente</h3>
      <p class="card-summary">TRAINING.md/CLAUDE.md de comportamento, spec em toda tarefa, contexto zerado a cada rodada.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        Cada agente tem um documento de comportamento fixo (padrão de código, linguagem, convenções).
        Toda tarefa chega com uma spec completa; o agente nunca carrega contexto de uma conversa
        para outra — cada rodada começa do zero, apoiada só na spec de entrada e no seu próprio
        documento de comportamento.
      </div>
    </article>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">05</span>
      <h3>Catálogo de ambientes</h3>
      <p class="card-summary">O agente sabe em qual máquina/ambiente está rodando.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        Um catálogo que descreve cada ambiente possível (máquina de estudo, PC corporativo,
        produção) é passado junto com a tarefa, para o agente nunca assumir errado onde está.
      </div>
    </article>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">06</span>
      <h3>Catálogo de serviços</h3>
      <p class="card-summary">Ações sensíveis (ex. commit em branch) via microsserviço determinístico, nunca agente-a-agente.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        Operações como "fazer commit em uma branch" passam por um microsserviço dedicado e
        determinístico do catálogo, nunca por uma chamada direta de um agente para outro —
        mesma lógica de guardrail da frente 3, aplicada a ações concretas.
      </div>
    </article>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">07</span>
      <h3>Entregável claro</h3>
      <p class="card-summary">Descrito na spec de entrada e reafirmado pelo agente na descrição do card que ele cria.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        O que conta como "pronto" vem descrito na spec de entrada — e o próprio agente que cria
        o card reafirma esse entregável na descrição, mesmo quando já parecia óbvio, para quem
        pegar o card depois não precisar adivinhar.
      </div>
    </article>
    <article class="card" data-status="planned" tabindex="0">
      <span class="card-num">09</span>
      <h3>Registro de agentes + portabilidade</h3>
      <p class="card-summary">Outro colaborador, ou o PC corporativo — mesmo projeto, roda em qualquer lugar.</p>
      <span class="card-tag">planejado</span>
      <div class="card-detail" hidden>
        Agentes ficam registrados em um lugar específico (banco de dados, ou até um card no Jira
        descrevendo cada um). Uma vez distribuídos os microsserviços de controle (Jira, credenciais,
        repositório), o projeto deve ser replicável em qualquer computador — outro colaborador, ou
        o PC corporativo do Leonardo.
      </div>
    </article>
  </section>

  <section class="column" data-status="open">
    <h2>Em aberto <span class="count">1</span></h2>
    <article class="card" data-status="open" tabindex="0">
      <span class="card-num">08</span>
      <h3>espolios.md</h3>
      <p class="card-summary">Opcional — pode ou não existir.</p>
      <span class="card-tag">em aberto</span>
      <div class="card-detail" hidden>
        Ideia em avaliação: ao fim de cada rodada, um arquivo espolios.md onde o agente sugere
        linhas que poderiam entrar no seu próprio TRAINING.md/CLAUDE.md para a próxima vez ser
        mais rápido. Ainda não decidido se vale o custo extra por rodada.
      </div>
    </article>
  </section>
</main>
```

- [ ] **Step 4: Write the column/card CSS, including per-status color mapping**

```css
.board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1.25rem;
  max-width: 1240px;
  margin: 0 auto;
  padding: 2.5rem 2rem 5rem;
}
.column { display: flex; flex-direction: column; gap: 0.85rem; min-width: 0; }
.column h2 {
  font-family: var(--font-display); font-size: 0.85rem; text-transform: uppercase;
  letter-spacing: 0.06em; color: var(--ink-faint); display: flex; justify-content: space-between;
  border-bottom: 1px solid var(--border); padding-bottom: 0.5rem; margin: 0;
}
.column .count { font-variant-numeric: tabular-nums; }
.column[data-status="done"] h2 { color: var(--status-done); }
.column[data-status="almost"] h2 { color: var(--accent-ink); }
.column[data-status="open"] h2 { color: var(--status-open); }

.card {
  background: var(--surface); border: 1px solid var(--border); border-radius: 6px;
  padding: 1rem 1.1rem; cursor: pointer;
}
.card:hover, .card:focus-visible { background: var(--surface-2); outline: none; }
.card:focus-visible { border-color: var(--accent); }
.card-num { font-family: var(--font-display); font-size: 0.72rem; color: var(--ink-faint); }
.card h3 { font-size: 0.98rem; margin: 0.3rem 0 0.35rem; }
.card-summary { font-size: 0.85rem; color: var(--ink-soft); margin: 0 0 0.5rem; }
.card-tag {
  font-family: var(--font-display); font-size: 0.68rem; text-transform: uppercase;
  letter-spacing: 0.05em; padding: 0.15rem 0.45rem; border-radius: 3px; border: 1px solid var(--border);
}
.card[data-status="almost"] .card-tag { color: var(--accent-ink); border-color: var(--accent); }
.card[data-status="planned"] .card-tag { color: var(--status-planned); }
.card[data-status="open"] .card-tag { color: var(--status-open); border-style: dashed; }
.card-detail { margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px dashed var(--border); font-size: 0.88rem; color: var(--ink-soft); }

@media (max-width: 900px) {
  .board { grid-template-columns: 1fr; }
}
```

- [ ] **Step 5: Publish and verify layout**

Publish via the `Artifact` tool (`favicon`: an emoji fitting an ops/agent theme, e.g. 🛰️). Verify manually:
- Page is dark by default with no light flash.
- All 9 cards present, correctly grouped: 0 in Concluído, 1 in Quase lá, 7 in Planejado, 1 in Em aberto.
- No purple/violet color anywhere (grep the file for common purple hex ranges as a sanity check).
- Narrow the viewport below 900px — columns stack, no horizontal scroll on `<body>`.

---

### Task 2: Click-to-expand card detail

**Files:**
- Modify: same file as Task 1.

**Interfaces:**
- Consumes: `.card` elements and their `.card-detail[hidden]` sibling from Task 1.
- Produces: a per-card `toggle()` closure wired to click and Enter/Space keypress, toggling the `hidden` attribute on that card's `.card-detail` and an `aria-expanded` state on the card itself — nothing downstream depends on this beyond the user interaction itself.

- [ ] **Step 1: Write the expand/collapse script**

```html
<script>
  document.querySelectorAll('.card').forEach((card) => {
    const detail = card.querySelector('.card-detail');
    card.setAttribute('aria-expanded', 'false');
    const toggle = () => {
      const isHidden = detail.hasAttribute('hidden');
      if (isHidden) { detail.removeAttribute('hidden'); card.setAttribute('aria-expanded', 'true'); }
      else { detail.setAttribute('hidden', ''); card.setAttribute('aria-expanded', 'false'); }
    };
    card.addEventListener('click', toggle);
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
    });
  });
</script>
```

- [ ] **Step 2: Verify manually**

Publish/update the artifact. Click a card — its detail panel appears below the summary, `card.getAttribute('aria-expanded')` becomes `"true"`. Click again — it collapses. Tab to a card with keyboard only, press Enter — same toggle happens without a mouse.

---

### Task 3: Ambient canvas + cursor spotlight (motion layer)

**Files:**
- Modify: same file as Tasks 1–2.

**Interfaces:**
- Consumes: `#hero-canvas` and `.hero-spotlight` elements from Task 1, and `.hero` as the bounding element for pointer coordinates.
- Produces: nothing consumed by later tasks — this is the last visual layer.

- [ ] **Step 1: Write the reduced-motion gate and node network animation**

```html
<script>
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const canvas = document.getElementById('hero-canvas');
  const hero = document.querySelector('.hero');

  function sizeCanvas() {
    canvas.width = hero.clientWidth;
    canvas.height = hero.clientHeight;
  }

  if (!prefersReducedMotion && canvas) {
    sizeCanvas();
    window.addEventListener('resize', sizeCanvas);
    const ctx = canvas.getContext('2d');
    const NODE_COUNT = 26;
    const nodes = Array.from({ length: NODE_COUNT }, () => ({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      vx: (Math.random() - 0.5) * 0.25,
      vy: (Math.random() - 0.5) * 0.25,
      pulse: Math.random() * Math.PI * 2,
    }));
    const LINK_DIST = 140;

    function frame() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      nodes.forEach((n) => {
        n.x += n.vx; n.y += n.vy; n.pulse += 0.02;
        if (n.x < 0 || n.x > canvas.width) n.vx *= -1;
        if (n.y < 0 || n.y > canvas.height) n.vy *= -1;
      });
      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const a = nodes[i], b = nodes[j];
          const dist = Math.hypot(a.x - b.x, a.y - b.y);
          if (dist < LINK_DIST) {
            ctx.strokeStyle = `rgba(255,157,46,${0.12 * (1 - dist / LINK_DIST)})`;
            ctx.lineWidth = 1;
            ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
          }
        }
      }
      nodes.forEach((n) => {
        const r = 1.6 + Math.sin(n.pulse) * 0.6;
        ctx.fillStyle = 'rgba(255,207,148,0.55)';
        ctx.beginPath(); ctx.arc(n.x, n.y, r, 0, Math.PI * 2); ctx.fill();
      });
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }
</script>
```

- [ ] **Step 2: Write the cursor spotlight script**

```html
<script>
  if (!prefersReducedMotion) {
    const spotlight = document.querySelector('.hero-spotlight');
    hero.addEventListener('pointermove', (e) => {
      const rect = hero.getBoundingClientRect();
      const x = ((e.clientX - rect.left) / rect.width) * 100;
      const y = ((e.clientY - rect.top) / rect.height) * 100;
      spotlight.style.background = `radial-gradient(circle at ${x}% ${y}%, rgba(255,157,46,0.10), transparent 45%)`;
    });
  }
</script>
```

- [ ] **Step 3: Verify manually**

Publish/update. Confirm: nodes drift and pulse gently in the hero, faint connecting lines appear when nodes are near each other, moving the mouse over the hero shifts the amber glow to follow the cursor. Then emulate `prefers-reduced-motion: reduce` (browser devtools rendering emulation) and reload — nodes freeze (script block skipped entirely) and the spotlight no longer follows the pointer; the static base spotlight from Task 1's CSS remains centered.

---

### Task 4: Final QA pass against the spec's "done" criteria

**Files:**
- No changes expected unless QA finds a gap — modify same file to fix.

- [ ] **Step 1: Re-read `docs/superpowers/specs/2026-08-07-ai-workforce-roadmap-design.md`'s "Critério de pronto" section and check each line against the published page**

- [ ] **Step 2: Confirm the footer note about the page being a living document is present**

```html
<footer class="page-footer">
  Este documento é vivo — mesmo link, atualizado conforme cada frente avança. O acompanhamento
  também continua nesta conversa.
</footer>
```

```css
.page-footer { max-width: 1240px; margin: 0 auto; padding: 0 2rem 3rem; color: var(--ink-faint); font-size: 0.82rem; }
```

- [ ] **Step 3: Publish the final version and share the artifact URL with the user**
