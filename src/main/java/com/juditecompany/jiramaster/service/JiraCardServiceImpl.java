package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.client.JiraApiClient;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.config.TarifaProperties;
import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;
import com.juditecompany.jiramaster.exception.CardNotFoundException;
import com.juditecompany.jiramaster.exception.JiraApiException;
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
