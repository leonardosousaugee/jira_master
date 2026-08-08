package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.client.JiraApiClient;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.config.TarifaProperties;
import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;
import com.juditecompany.jiramaster.exception.CardNotFoundException;
import com.juditecompany.jiramaster.exception.JiraApiException;
import com.juditecompany.jiramaster.exception.LinhaDeCustoNaoEncontradaException;
import com.juditecompany.jiramaster.exception.ModeloDesconhecidoException;
import com.juditecompany.jiramaster.exception.SubtaskIssueTypeNotFoundException;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.ledger.LedgerDeCusto;
import com.juditecompany.jiramaster.ledger.LinhaCusto;
import com.juditecompany.jiramaster.mapper.AdfMapper;
import com.juditecompany.jiramaster.mapper.JiraCardMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class JiraCardServiceImpl implements JiraCardService {

    private final JiraApiClient jiraApiClient;
    private final JiraCardMapper cardMapper;
    private final AdfMapper adfMapper;
    private final JiraProperties jiraProperties;
    private final TarifaProperties tarifaProperties;
    private final LedgerDeCusto ledger;
    private final Clock relogio;
    private final Map<String, String> tipoDeSubtarefaPorProjeto = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locksPorCard = new ConcurrentHashMap<>();

    private static final BigDecimal POR_MILHAO = BigDecimal.valueOf(1_000_000);
    private static final DateTimeFormatter FORMATO_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    // BLOQUEADO e HOLD sao o mesmo estado. O fluxo do KAN so tem HOLD; os outros nomes existem
    // para o servico funcionar em board que chame a mesma etapa de outro jeito.
    private static final Set<String> NOMES_DE_HOLD = Set.of("HOLD", "BLOQUEADO", "BLOCKED", "BLOCK");

    public JiraCardServiceImpl(JiraApiClient jiraApiClient, JiraCardMapper cardMapper,
                                AdfMapper adfMapper, JiraProperties jiraProperties,
                                TarifaProperties tarifaProperties, LedgerDeCusto ledger, Clock relogio) {
        this.jiraApiClient = jiraApiClient;
        this.cardMapper = cardMapper;
        this.adfMapper = adfMapper;
        this.jiraProperties = jiraProperties;
        this.tarifaProperties = tarifaProperties;
        this.ledger = ledger;
        this.relogio = relogio;
    }

    @Override
    public CardResponse criarCard(CriarCardRequest request) {
        String projectKey = request.projectKey() != null ? request.projectKey() : jiraProperties.getDefaultProjectKey();
        String tipoIssue = request.tipoIssue() != null ? request.tipoIssue() : "Tarefa";
        Object descricaoAdf = request.descricao() != null ? adfMapper.textoParaAdf(request.descricao()) : null;

        var fields = new JiraIssueFields(new JiraFieldRef(projectKey), request.titulo(), descricaoAdf,
                new JiraNameRef(tipoIssue), null, null);
        JiraCreatedIssueDto criado = jiraApiClient.criarIssue(new JiraIssueRequest(fields));

        return cardMapper.paraCardResponse(jiraApiClient.buscarIssuePorChave(criado.key()));
    }

    @Override
    public List<CardResumoResponse> lerCardsEmAberto(String projectKeyOverride) {
        String projectKey = projectKeyOverride != null ? projectKeyOverride : jiraProperties.getDefaultProjectKey();
        String jql = "project = " + projectKey + " AND statusCategory != Done ORDER BY created DESC";

        return jiraApiClient.buscarIssues(jql).issues().stream()
                .map(cardMapper::paraCardResumoResponse)
                .toList();
    }

    @Override
    public CardResponse buscarCardPorId(String issueKey) {
        return cardMapper.paraCardResponse(buscarIssueOuLancarNaoEncontrado(issueKey));
    }

    @Override
    public CardResponse editarCard(String issueKey, EditarCardRequest request) {
        Object descricaoAdf = null;
        if (request.descricao() != null) {
            // O bloco de custo e preservado: quem edita a descricao esta editando o texto humano,
            // e apagar o ledger junto seria destruir o numero em que o sistema confia para parar.
            String atual = adfMapper.adfParaTexto(buscarIssueOuLancarNaoEncontrado(issueKey).fields().description());
            LedgerDeCusto.Partes partes = ledger.separar(atual);
            String completa = partes.temBloco()
                    ? request.descricao() + "\n\n" + LedgerDeCusto.ABERTURA + "\n"
                            + partes.conteudoBloco() + "\n" + LedgerDeCusto.FECHAMENTO
                    : request.descricao();
            descricaoAdf = descricaoParaAdf(completa);
        }
        var fields = new JiraIssueFields(null, request.titulo(), descricaoAdf, null, null, null);

        atualizarIssueOuLancarNaoEncontrado(issueKey, fields);

        return cardMapper.paraCardResponse(buscarIssueOuLancarNaoEncontrado(issueKey));
    }

    @Override
    public LinhaCusto registrarCusto(String issueKey, RegistrarCustoRequest request) {
        TarifaProperties.Tarifa tarifa = tarifaProperties.getTarifas().get(request.modelo());
        if (tarifa == null) {
            throw new ModeloDesconhecidoException(request.modelo(), tarifaProperties.modelosConhecidos());
        }

        JiraIssueDto card = buscarIssueOuLancarNaoEncontrado(issueKey);
        JiraFieldRef pai = card.fields().parent();
        String chaveAlvo = pai != null ? pai.key() : issueKey;

        long entrada = request.inputTokens() + request.cacheCreationTokens() + request.cacheReadTokens();
        var linha = new LinhaCusto(
                LocalDateTime.now(relogio).truncatedTo(ChronoUnit.MINUTES).format(FORMATO_TS),
                issueKey, entrada, request.outputTokens(), calcularUsd(request, tarifa));

        // O Jira nao oferece compare-and-swap em update de issue, entao o lock em processo e a
        // unica protecao contra duas escritas concorrentes perderem uma linha. Isso torna a
        // instancia unica do servico uma condicao de corretude, nao uma preferencia.
        ReentrantLock lock = locksPorCard.computeIfAbsent(chaveAlvo, chave -> new ReentrantLock());
        lock.lock();
        try {
            JiraIssueDto alvo = chaveAlvo.equals(issueKey) ? card : buscarIssueOuLancarNaoEncontrado(chaveAlvo);
            String atual = adfMapper.adfParaTexto(alvo.fields().description());
            String nova = ledger.acrescentar(atual, linha);

            atualizarIssueOuLancarNaoEncontrado(chaveAlvo,
                    new JiraIssueFields(null, null, descricaoParaAdf(nova), null, null, null));
        } finally {
            lock.unlock();
        }
        return linha;
    }

    @Override
    public List<CardResumoResponse> listarSubCards(String issueKey) {
        JiraIssueDto card = buscarIssueOuLancarNaoEncontrado(issueKey);
        List<JiraIssueDto> filhos = card.fields().subtasks();
        if (filhos == null) {
            return List.of();
        }
        return filhos.stream().map(cardMapper::paraCardResumoResponse).toList();
    }

    @Override
    public CustoArvoreResponse lerCustoDaArvore(String issueKey) {
        JiraIssueDto card = buscarIssueOuLancarNaoEncontrado(issueKey);
        JiraFieldRef pai = card.fields().parent();

        // O bloco vive no card pai, entao a arvore de uma subtarefa e ela mesma: le-se o bloco do
        // pai e filtra-se pelas linhas dela.
        JiraIssueDto portador = pai != null ? buscarIssueOuLancarNaoEncontrado(pai.key()) : card;
        LedgerDeCusto.Leitura leitura = ledger.ler(adfMapper.adfParaTexto(portador.fields().description()));

        List<LinhaCusto> relevantes = pai != null
                ? leitura.linhas().stream().filter(linha -> issueKey.equals(linha.card())).toList()
                : leitura.linhas();

        Map<String, List<LinhaCusto>> porChave = relevantes.stream()
                .collect(Collectors.groupingBy(LinhaCusto::card, LinkedHashMap::new, Collectors.toList()));

        List<CustoArvoreResponse.CustoPorCard> porCard = porChave.entrySet().stream()
                .map(entrada -> new CustoArvoreResponse.CustoPorCard(entrada.getKey(),
                        somar(entrada.getValue()), entrada.getValue().size()))
                .toList();

        List<String> semCusto = pai != null ? List.of() : chavesDosFilhos(card).stream()
                .filter(chave -> !porChave.containsKey(chave))
                .toList();

        return new CustoArvoreResponse(issueKey,
                somarOuNulo(porChave.getOrDefault(issueKey, List.of())),
                porCard,
                somarOuNulo(relevantes),
                semCusto,
                leitura.linhasDescartadas());
    }

    /**
     * Sem nenhuma linha o valor e nulo, nao zero. Um card medido cujas linhas se anulam vale zero;
     * um card nunca medido nao vale nada — e tratar os dois como iguais faria o total mentir para
     * baixo justamente no numero que autoriza continuar gastando.
     */
    private BigDecimal somarOuNulo(List<LinhaCusto> linhas) {
        return linhas.isEmpty() ? null : somar(linhas);
    }

    private List<String> chavesDosFilhos(JiraIssueDto card) {
        List<JiraIssueDto> filhos = card.fields().subtasks();
        return filhos == null ? List.of() : filhos.stream().map(JiraIssueDto::key).toList();
    }

    private BigDecimal somar(List<LinhaCusto> linhas) {
        return linhas.stream().map(LinhaCusto::usd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public LinhaCusto estornarCusto(String issueKey, EstornarCustoRequest request) {
        String chaveAlvo = resolverPortadorDoBloco(issueKey);

        ReentrantLock lock = locksPorCard.computeIfAbsent(chaveAlvo, chave -> new ReentrantLock());
        lock.lock();
        try {
            String atual = adfMapper.adfParaTexto(
                    buscarIssueOuLancarNaoEncontrado(chaveAlvo).fields().description());
            LedgerDeCusto.Leitura leitura = ledger.ler(atual);

            LinhaCusto original = leitura.linhas().stream()
                    .filter(linha -> !linha.ehEstorno()
                            && linha.ts().equals(request.ts()) && linha.card().equals(request.card()))
                    .findFirst()
                    .orElseThrow(() -> new LinhaDeCustoNaoEncontradaException(request.ts(), request.card(), chaveAlvo));

            String referencia = original.ts() + "|" + original.card();
            boolean jaEstornada = leitura.linhas().stream()
                    .anyMatch(linha -> referencia.equals(linha.estorna()));
            if (jaEstornada) {
                throw new LinhaDeCustoNaoEncontradaException(request.ts(), request.card(),
                        chaveAlvo + " (a linha ja foi estornada)");
            }

            var estorno = new LinhaCusto(
                    LocalDateTime.now(relogio).truncatedTo(ChronoUnit.MINUTES).format(FORMATO_TS),
                    original.card(), -original.in(), -original.out(), original.usd().negate(), referencia);

            atualizarIssueOuLancarNaoEncontrado(chaveAlvo, new JiraIssueFields(null, null,
                    descricaoParaAdf(ledger.acrescentar(atual, estorno)), null, null, null));
            return estorno;
        } finally {
            lock.unlock();
        }
    }

    private String resolverPortadorDoBloco(String issueKey) {
        JiraFieldRef pai = buscarIssueOuLancarNaoEncontrado(issueKey).fields().parent();
        return pai != null ? pai.key() : issueKey;
    }

    /**
     * Pai primeiro, filhos depois: o pai em HOLD e a flag que o health check entre blocos le, entao
     * fecha-se a porta de entrada antes de sair fechando as janelas. Na ordem inversa existe uma
     * janela em que os filhos ja pararam e o pai ainda aceita trabalho novo.
     */
    @Override
    public ResultadoHoldResponse moverArvoreParaHold(String issueKey) {
        JiraIssueDto card = buscarIssueOuLancarNaoEncontrado(issueKey);

        List<ResultadoHoldResponse.CardMovido> resultados = new ArrayList<>();
        resultados.add(moverParaHold(issueKey));
        for (String filho : chavesDosFilhos(card)) {
            resultados.add(moverParaHold(filho));
        }
        return new ResultadoHoldResponse(List.copyOf(resultados));
    }

    private ResultadoHoldResponse.CardMovido moverParaHold(String issueKey) {
        try {
            JiraIssueDto card = buscarIssueOuLancarNaoEncontrado(issueKey);
            if (ehHold(card.fields().status().name())) {
                return new ResultadoHoldResponse.CardMovido(issueKey, true, "ja estava em HOLD");
            }

            return jiraApiClient.buscarTransicoes(issueKey).transitions().stream()
                    .filter(transicao -> ehHold(transicao.to().name()))
                    .findFirst()
                    .map(transicao -> {
                        jiraApiClient.executarTransicao(issueKey, transicao.id());
                        return new ResultadoHoldResponse.CardMovido(issueKey, true, null);
                    })
                    .orElseGet(() -> new ResultadoHoldResponse.CardMovido(issueKey, false,
                            "nenhuma transicao para HOLD a partir de \"" + card.fields().status().name() + "\""));
        } catch (RuntimeException ex) {
            // Kill switch nao pode falhar inteiro por causa de um card: reporta e segue.
            return new ResultadoHoldResponse.CardMovido(issueKey, false, ex.getMessage());
        }
    }

    /** BLOQUEADO e HOLD sao o mesmo estado; o fluxo do KAN so tem HOLD, os demais sao sinonimos. */
    private boolean ehHold(String nomeEtapa) {
        return nomeEtapa != null && NOMES_DE_HOLD.contains(nomeEtapa.trim().toUpperCase(Locale.ROOT));
    }

    private BigDecimal calcularUsd(RegistrarCustoRequest request, TarifaProperties.Tarifa tarifa) {
        BigDecimal total = tarifa.input().multiply(BigDecimal.valueOf(request.inputTokens()))
                .add(tarifa.cacheWrite().multiply(BigDecimal.valueOf(request.cacheCreationTokens())))
                .add(tarifa.cacheRead().multiply(BigDecimal.valueOf(request.cacheReadTokens())))
                .add(tarifa.output().multiply(BigDecimal.valueOf(request.outputTokens())));
        return total.divide(POR_MILHAO, 4, RoundingMode.HALF_UP);
    }

    /**
     * O bloco vai num no codeBlock e o texto humano em um paragrafo por linha — ADF nao renderiza
     * \n dentro de um no de texto, entao bloco multi-linha como paragrafo colapsaria numa linha so.
     */
    private Object descricaoParaAdf(String textoCompleto) {
        LedgerDeCusto.Partes partes = ledger.separar(textoCompleto);
        if (!partes.temBloco()) {
            return adfMapper.textoParaAdf(textoCompleto);
        }

        List<Map<String, Object>> nos = new ArrayList<>(
                adfMapper.paragrafosDeTexto(partes.antes().stripTrailing()));
        nos.add(adfMapper.blocoDeCodigo(
                LedgerDeCusto.ABERTURA + "\n" + partes.conteudoBloco() + "\n" + LedgerDeCusto.FECHAMENTO));
        nos.addAll(adfMapper.paragrafosDeTexto(partes.depois()));
        return adfMapper.documento(nos);
    }

    @Override
    public List<TransicaoResponse> listarTransicoesDisponiveis(String issueKey) {
        return jiraApiClient.buscarTransicoes(issueKey).transitions().stream()
                .map(cardMapper::paraTransicaoResponse)
                .toList();
    }

    @Override
    public void alterarEtapaCard(String issueKey, AlterarEtapaRequest request) {
        List<JiraTransitionDto> transicoes = jiraApiClient.buscarTransicoes(issueKey).transitions();

        JiraTransitionDto transicaoEncontrada = transicoes.stream()
                .filter(t -> t.to().name().equalsIgnoreCase(request.etapaDestino()))
                .findFirst()
                .orElseThrow(() -> new TransitionNotFoundException(
                        issueKey, request.etapaDestino(),
                        transicoes.stream().map(t -> t.to().name()).toList()));

        jiraApiClient.executarTransicao(issueKey, transicaoEncontrada.id());
    }

    @Override
    public CardResponse adicionarSubCard(String issueKeyPai, AdicionarSubCardRequest request) {
        String projectKey = jiraProperties.getDefaultProjectKey();
        Object descricaoAdf = request.descricao() != null ? adfMapper.textoParaAdf(request.descricao()) : null;
        var fields = new JiraIssueFields(new JiraFieldRef(projectKey), request.titulo(), descricaoAdf,
                JiraNameRef.porId(resolverTipoDeSubtarefaId(projectKey)), new JiraFieldRef(issueKeyPai), null);

        JiraCreatedIssueDto criado = jiraApiClient.criarIssue(new JiraIssueRequest(fields));

        return cardMapper.paraCardResponse(jiraApiClient.buscarIssuePorChave(criado.key()));
    }

    /**
     * O nome do tipo e localizado ("Subtask" em ingles, "Subtarefa" em portugues) e o usuario
     * pode renomear; o id nao muda. Por isso a subtarefa referencia o tipo por id, descoberto
     * lendo os tipos do proprio projeto, com o id fixo em configuracao como escape.
     */
    private String resolverTipoDeSubtarefaId(String projectKey) {
        String configurado = jiraProperties.getSubtaskIssueTypeId();
        if (configurado != null && !configurado.isBlank()) {
            return configurado;
        }
        return tipoDeSubtarefaPorProjeto.computeIfAbsent(projectKey, this::descobrirTipoDeSubtarefaId);
    }

    private String descobrirTipoDeSubtarefaId(String projectKey) {
        List<JiraIssueTypeDto> tipos = jiraApiClient.buscarProjeto(projectKey).issueTypes();

        return tipos.stream()
                .filter(JiraIssueTypeDto::subtask)
                .map(JiraIssueTypeDto::id)
                .findFirst()
                .orElseThrow(() -> new SubtaskIssueTypeNotFoundException(projectKey,
                        tipos.stream().map(JiraIssueTypeDto::name).toList()));
    }

    @Override
    public void editarPrioridade(String issueKey, EditarPrioridadeRequest request) {
        var fields = new JiraIssueFields(null, null, null, null, null, new JiraNameRef(request.prioridade()));
        atualizarIssueOuLancarNaoEncontrado(issueKey, fields);
    }

    @Override
    public ComentarioResponse adicionarComentario(String issueKey, AdicionarComentarioRequest request) {
        JiraCommentDto criado = jiraApiClient.adicionarComentario(issueKey, adfMapper.textoParaAdf(request.comentario()));
        return cardMapper.paraComentarioResponse(criado);
    }

    private JiraIssueDto buscarIssueOuLancarNaoEncontrado(String issueKey) {
        try {
            return jiraApiClient.buscarIssuePorChave(issueKey);
        } catch (JiraApiException ex) {
            if (ex.getStatus().equals(HttpStatus.NOT_FOUND)) {
                throw new CardNotFoundException(issueKey);
            }
            throw ex;
        }
    }

    private void atualizarIssueOuLancarNaoEncontrado(String issueKey, JiraIssueFields fields) {
        try {
            jiraApiClient.atualizarIssue(issueKey, new JiraIssueRequest(fields));
        } catch (JiraApiException ex) {
            if (ex.getStatus().equals(HttpStatus.NOT_FOUND)) {
                throw new CardNotFoundException(issueKey);
            }
            throw ex;
        }
    }
}
