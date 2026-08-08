package com.juditecompany.jiramaster.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juditecompany.jiramaster.client.JiraApiClient;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.config.TarifaProperties;
import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.exception.CardNotFoundException;
import com.juditecompany.jiramaster.exception.JiraApiException;
import com.juditecompany.jiramaster.exception.ModeloDesconhecidoException;
import com.juditecompany.jiramaster.exception.SubtaskIssueTypeNotFoundException;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.ledger.LedgerDeCusto;
import com.juditecompany.jiramaster.ledger.LinhaCusto;
import com.juditecompany.jiramaster.mapper.AdfMapper;
import com.juditecompany.jiramaster.mapper.JiraCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraCardServiceImplTest {

    @Mock
    private JiraApiClient jiraApiClient;

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-08-08T13:04:30Z"), ZoneOffset.UTC);

    private JiraCardServiceImpl service;
    private final AdfMapper adfMapper = new AdfMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LedgerDeCusto ledger = new LedgerDeCusto(objectMapper);
    private final JiraCardMapper cardMapper = new JiraCardMapper(adfMapper, ledger);

    private JiraIssueDto issueDeExemplo(String key, String statusNome) {
        return issueDeExemplo(key, statusNome, null, null);
    }

    private JiraIssueDto issueDeExemplo(String key, String statusNome, String chavePai, String descricao) {
        JsonNode descricaoAdf = descricao == null
                ? null
                : objectMapper.valueToTree(adfMapper.textoParaAdf(descricao));
        var fields = new JiraIssueResponseFields(
                "Titulo", descricaoAdf, new JiraStatusDto(statusNome), new JiraNameRef("Medium"),
                new JiraNameRef("Task"), new JiraFieldRef("KAN"),
                chavePai == null ? null : new JiraFieldRef(chavePai),
                "2026-08-05T10:00:00.000+0000", "2026-08-05T10:00:00.000+0000");
        return new JiraIssueDto("10001", key, fields);
    }

    private TarifaProperties tarifasDeTeste() {
        TarifaProperties tarifas = new TarifaProperties();
        tarifas.setTarifas(new java.util.LinkedHashMap<>(Map.of("claude-opus-5",
                new TarifaProperties.Tarifa(new BigDecimal("5.00"), new BigDecimal("25.00"),
                        new BigDecimal("6.25"), new BigDecimal("0.50")))));
        return tarifas;
    }

    private JiraProperties propriedadesDeTeste() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl("https://juditecompany.atlassian.net");
        properties.setEmail("leonardo.sousa@witzler-ultragaz.com.br");
        properties.setApiToken("token-de-teste");
        properties.setDefaultProjectKey("KAN");
        return properties;
    }

    private JiraProjectDto projetoComTipos() {
        return new JiraProjectDto("10000", "KAN", List.of(
                new JiraIssueTypeDto("10001", "Epic", false),
                new JiraIssueTypeDto("10002", "Subtarefa", true),
                new JiraIssueTypeDto("10003", "Tarefa", false)));
    }

    @BeforeEach
    void setUp() {
        service = new JiraCardServiceImpl(jiraApiClient, cardMapper, adfMapper, propriedadesDeTeste(), tarifasDeTeste(), ledger, RELOGIO);
    }

    @Test
    void deveCriarCardEBuscarDetalhesCompletosDepois() {
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10001", "KAN-1"));
        when(jiraApiClient.buscarIssuePorChave("KAN-1")).thenReturn(issueDeExemplo("KAN-1", "To Do"));

        CardResponse resultado = service.criarCard(new CriarCardRequest("Titulo", "Descricao", "Task", null));

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
        verify(jiraApiClient).criarIssue(argThat(req -> req.fields().project().key().equals("KAN")));
    }

    @Test
    void deveMontarJqlComProjectKeyPadraoQuandoNenhumForInformado() {
        when(jiraApiClient.buscarIssues(anyString())).thenReturn(new JiraSearchResponseDto(List.of(issueDeExemplo("KAN-1", "To Do"))));

        service.lerCardsEmAberto(null);

        verify(jiraApiClient).buscarIssues("project = KAN AND statusCategory != Done ORDER BY created DESC");
    }

    @Test
    void deveMontarJqlComProjectKeyInformado() {
        when(jiraApiClient.buscarIssues(anyString())).thenReturn(new JiraSearchResponseDto(List.of()));

        service.lerCardsEmAberto("OUTRO");

        verify(jiraApiClient).buscarIssues("project = OUTRO AND statusCategory != Done ORDER BY created DESC");
    }

    @Test
    void deveBuscarCardPorId() {
        when(jiraApiClient.buscarIssuePorChave("KAN-1")).thenReturn(issueDeExemplo("KAN-1", "To Do"));

        CardResponse resultado = service.buscarCardPorId("KAN-1");

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
    }

    @Test
    void deveTraduzirJiraApiException404ParaCardNotFoundException() {
        when(jiraApiClient.buscarIssuePorChave("KAN-999")).thenThrow(new JiraApiException(HttpStatus.NOT_FOUND, "nao existe"));

        assertThatThrownBy(() -> service.buscarCardPorId("KAN-999"))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void deveEditarCardEDevolverEstadoAtualizado() {
        when(jiraApiClient.buscarIssuePorChave("KAN-1")).thenReturn(issueDeExemplo("KAN-1", "To Do"));

        CardResponse resultado = service.editarCard("KAN-1", new EditarCardRequest("Novo titulo", null));

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
        verify(jiraApiClient).atualizarIssue(eq("KAN-1"), argThat(req -> req.fields().summary().equals("Novo titulo")));
    }

    @Test
    void deveListarTransicoesDisponiveis() {
        when(jiraApiClient.buscarTransicoes("KAN-1")).thenReturn(new JiraTransitionsResponseDto(
                List.of(new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress")))));

        var resultado = service.listarTransicoesDisponiveis("KAN-1");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nomeEtapaDestino()).isEqualTo("In Progress");
    }

    @Test
    void deveAlterarEtapaQuandoNomeCasaComTransicaoDisponivel() {
        when(jiraApiClient.buscarTransicoes("KAN-1")).thenReturn(new JiraTransitionsResponseDto(
                List.of(new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress")))));

        service.alterarEtapaCard("KAN-1", new AlterarEtapaRequest("in progress"));

        verify(jiraApiClient).executarTransicao("KAN-1", "31");
    }

    @Test
    void deveLancarTransitionNotFoundQuandoNomeNaoCasaComNenhumaTransicao() {
        when(jiraApiClient.buscarTransicoes("KAN-1")).thenReturn(new JiraTransitionsResponseDto(
                List.of(new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress")))));

        assertThatThrownBy(() -> service.alterarEtapaCard("KAN-1", new AlterarEtapaRequest("Bloqueado")))
                .isInstanceOf(TransitionNotFoundException.class);
    }

    @Test
    void deveAdicionarSubCardComParentReferenciadoETipoDescobertoNoProjeto() {
        when(jiraApiClient.buscarProjeto("KAN")).thenReturn(projetoComTipos());
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10002", "KAN-2"));
        when(jiraApiClient.buscarIssuePorChave("KAN-2")).thenReturn(issueDeExemplo("KAN-2", "To Do"));

        service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Subtarefa", "Descricao"));

        verify(jiraApiClient).criarIssue(argThat(req ->
                req.fields().parent() != null && req.fields().parent().key().equals("KAN-1")
                        && req.fields().issuetype().id().equals("10002")
                        && req.fields().issuetype().name() == null));
    }

    @Test
    void deveDescobrirTipoDeSubtarefaUmaVezSoEReaproveitar() {
        when(jiraApiClient.buscarProjeto("KAN")).thenReturn(projetoComTipos());
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10002", "KAN-2"));
        when(jiraApiClient.buscarIssuePorChave("KAN-2")).thenReturn(issueDeExemplo("KAN-2", "To Do"));

        service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Uma", null));
        service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Outra", null));

        verify(jiraApiClient, times(1)).buscarProjeto("KAN");
    }

    @Test
    void devePreferirTipoDeSubtarefaConfiguradoSemConsultarOProjeto() {
        JiraProperties properties = propriedadesDeTeste();
        properties.setSubtaskIssueTypeId("99999");
        service = new JiraCardServiceImpl(jiraApiClient, cardMapper, adfMapper, properties, tarifasDeTeste(), ledger, RELOGIO);
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10002", "KAN-2"));
        when(jiraApiClient.buscarIssuePorChave("KAN-2")).thenReturn(issueDeExemplo("KAN-2", "To Do"));

        service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Subtarefa", null));

        verify(jiraApiClient, never()).buscarProjeto(anyString());
        verify(jiraApiClient).criarIssue(argThat(req -> req.fields().issuetype().id().equals("99999")));
    }

    @Test
    void deveFalharQuandoProjetoNaoTemNenhumTipoDeSubtarefa() {
        when(jiraApiClient.buscarProjeto("KAN")).thenReturn(new JiraProjectDto("10000", "KAN", List.of(
                new JiraIssueTypeDto("10003", "Tarefa", false))));

        assertThatThrownBy(() -> service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Subtarefa", null)))
                .isInstanceOf(SubtaskIssueTypeNotFoundException.class);

        verify(jiraApiClient, never()).criarIssue(any());
    }

    @Test
    void deveGravarCustoNoCardPaiEMarcarALinhaComAChaveDoFilho() {
        when(jiraApiClient.buscarIssuePorChave("KAN-15")).thenReturn(issueDeExemplo("KAN-15", "To Do", "KAN-5", null));
        when(jiraApiClient.buscarIssuePorChave("KAN-5")).thenReturn(issueDeExemplo("KAN-5", "To Do", null, "Texto humano"));

        LinhaCusto linha = service.registrarCusto("KAN-15",
                new RegistrarCustoRequest("claude-opus-5", 1000, 1000, 1_000_000, 1000));

        assertThat(linha.card()).isEqualTo("KAN-15");
        assertThat(linha.ts()).isEqualTo("2026-08-08T13:04");
        assertThat(linha.in()).isEqualTo(1_002_000);
        assertThat(linha.out()).isEqualTo(1000);
        verify(jiraApiClient).atualizarIssue(eq("KAN-5"), any());
        verify(jiraApiClient, never()).atualizarIssue(eq("KAN-15"), any());
    }

    @Test
    void deveCalcularUsdComAsQuatroTarifasEnaoComUmaSo() {
        when(jiraApiClient.buscarIssuePorChave("KAN-5")).thenReturn(issueDeExemplo("KAN-5", "To Do", null, null));

        LinhaCusto linha = service.registrarCusto("KAN-5",
                new RegistrarCustoRequest("claude-opus-5", 1000, 1000, 1_000_000, 1000));

        // 1000*5 + 1000*6,25 + 1000000*0,50 + 1000*25 = 536250 / 1e6
        assertThat(linha.usd()).isEqualByComparingTo(new BigDecimal("0.5363"));
        // Se cache_read fosse cobrado como input cheio daria 5.0363 — quase 10x. E a razao de o
        // servico receber os quatro contadores separados em vez de um total ja somado.
    }

    @Test
    void deveGravarNoProprioCardQuandoNaoTemPai() {
        when(jiraApiClient.buscarIssuePorChave("KAN-3")).thenReturn(issueDeExemplo("KAN-3", "To Do", null, null));

        service.registrarCusto("KAN-3", new RegistrarCustoRequest("claude-opus-5", 10, 0, 0, 10));

        verify(jiraApiClient).atualizarIssue(eq("KAN-3"), any());
    }

    @Test
    void deveRecusarModeloSemTarifaEnaoGravarNada() {
        assertThatThrownBy(() -> service.registrarCusto("KAN-5",
                new RegistrarCustoRequest("modelo-inventado", 10, 0, 0, 10)))
                .isInstanceOf(ModeloDesconhecidoException.class);

        verify(jiraApiClient, never()).atualizarIssue(anyString(), any());
    }

    @Test
    void deveAcumularDuasExecucoesComoDuasLinhasSemPerderNenhuma() {
        String descricaoComUmaLinha = "Texto humano\n\n" + LedgerDeCusto.ABERTURA
                + "\n{\"ts\":\"2026-08-08T11:00\",\"card\":\"KAN-5\",\"in\":10,\"out\":5,\"usd\":0.01}\n"
                + LedgerDeCusto.FECHAMENTO;
        when(jiraApiClient.buscarIssuePorChave("KAN-5"))
                .thenReturn(issueDeExemplo("KAN-5", "To Do", null, descricaoComUmaLinha));

        service.registrarCusto("KAN-5", new RegistrarCustoRequest("claude-opus-5", 10, 0, 0, 10));

        verify(jiraApiClient).atualizarIssue(eq("KAN-5"), argThat(req -> {
            String texto = adfMapper.adfParaTexto(objectMapper.valueToTree(req.fields().description()));
            LedgerDeCusto.Leitura leitura = ledger.ler(texto);
            return leitura.linhas().size() == 2 && texto.contains("Texto humano");
        }));
    }

    @Test
    void deveEditarPrioridadeEnviandoApenasEsseCampo() {
        service.editarPrioridade("KAN-1", new EditarPrioridadeRequest("High"));

        verify(jiraApiClient).atualizarIssue(eq("KAN-1"), argThat(req ->
                req.fields().priority().name().equals("High") && req.fields().summary() == null));
    }

    @Test
    void deveAdicionarComentario() {
        when(jiraApiClient.adicionarComentario(eq("KAN-1"), any()))
                .thenReturn(new JiraCommentDto("10050",
                        new JiraCommentAuthorDto("Leonardo"), null, "2026-08-05T10:00:00.000+0000"));

        var resultado = service.adicionarComentario("KAN-1", new AdicionarComentarioRequest("Comentario"));

        assertThat(resultado.autor()).isEqualTo("Leonardo");
    }
}
