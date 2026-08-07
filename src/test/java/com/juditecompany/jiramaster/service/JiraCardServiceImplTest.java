package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.client.JiraApiClient;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.exception.CardNotFoundException;
import com.juditecompany.jiramaster.exception.JiraApiException;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.mapper.AdfMapper;
import com.juditecompany.jiramaster.mapper.JiraCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraCardServiceImplTest {

    @Mock
    private JiraApiClient jiraApiClient;

    private JiraCardServiceImpl service;
    private final AdfMapper adfMapper = new AdfMapper();
    private final JiraCardMapper cardMapper = new JiraCardMapper(adfMapper);

    private JiraIssueDto issueDeExemplo(String key, String statusNome) {
        var fields = new JiraIssueResponseFields(
                "Titulo", null, new JiraStatusDto(statusNome), new JiraNameRef("Medium"),
                new JiraNameRef("Task"), new JiraFieldRef("KAN"),
                "2026-08-05T10:00:00.000+0000", "2026-08-05T10:00:00.000+0000");
        return new JiraIssueDto("10001", key, fields);
    }

    @BeforeEach
    void setUp() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl("https://juditecompany.atlassian.net");
        properties.setEmail("leonardo.sousa@witzler-ultragaz.com.br");
        properties.setApiToken("token-de-teste");
        properties.setDefaultProjectKey("KAN");

        service = new JiraCardServiceImpl(jiraApiClient, cardMapper, adfMapper, properties);
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
    void deveAdicionarSubCardComParentReferenciado() {
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10002", "KAN-2"));
        when(jiraApiClient.buscarIssuePorChave("KAN-2")).thenReturn(issueDeExemplo("KAN-2", "To Do"));

        service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Subtarefa", "Descricao"));

        verify(jiraApiClient).criarIssue(argThat(req ->
                req.fields().parent() != null && req.fields().parent().key().equals("KAN-1")
                        && req.fields().issuetype().name().equals("Subtask")));
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
