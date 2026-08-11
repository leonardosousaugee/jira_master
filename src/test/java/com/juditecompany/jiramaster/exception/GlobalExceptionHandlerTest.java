package com.juditecompany.jiramaster.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveRetornar404ParaCardNaoEncontrado() {
        ProblemDetail problema = handler.handleCardNaoEncontrado(new CardNotFoundException("KAN-999"));

        assertThat(problema.getStatus()).isEqualTo(404);
        assertThat(problema.getDetail()).contains("KAN-999");
    }

    @Test
    void deveRetornar400ComTransicoesDisponiveis() {
        TransitionNotFoundException ex = new TransitionNotFoundException(
                "KAN-1", "Bloqueado", List.of("To Do", "In Progress", "Done"));

        ProblemDetail problema = handler.handleTransicaoNaoEncontrada(ex);

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getProperties())
                .containsEntry("transicoesDisponiveis", List.of("To Do", "In Progress", "Done"));
    }

    @Test
    void deveRetornar422QuandoOCampoWorkerNaoExisteNaInstancia() {
        ProblemDetail problema = handler.handleCampoWorkerNaoDisponivel(new CampoWorkerNaoDisponivelException());

        assertThat(problema.getStatus()).isEqualTo(422);
        assertThat(problema.getDetail()).contains("worker-field-id");
    }

    @Test
    void deveRetornar409ComAEtapaAtualQuandoOCardEstaEmHold() {
        ProblemDetail problema = handler.handleCardEmHold(
                new CardEmHoldException("KAN-1", "HOLD", "Em andamento"));

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getProperties()).containsEntry("etapaAtual", "HOLD");
        assertThat(problema.getDetail()).contains("KAN-1").contains("Em andamento");
    }

    @Test
    void devePropagarStatusDaJiraApiException() {
        JiraApiException ex = new JiraApiException(HttpStatus.UNAUTHORIZED, "{\"errorMessages\":[\"token invalido\"]}");

        ProblemDetail problema = handler.handleJiraApiException(ex);

        assertThat(problema.getStatus()).isEqualTo(401);
    }

    @Test
    void deveRetornar400ParaErroDeValidacao() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError erro = new FieldError("objeto", "titulo", "nao pode ser vazio");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(erro));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail problema = handler.handleValidacao(ex);

        assertThat(problema.getStatus()).isEqualTo(400);
    }

    @Test
    void deveRetornar500ParaErroGenerico() {
        ProblemDetail problema = handler.handleErroGenerico(new RuntimeException("boom"));

        assertThat(problema.getStatus()).isEqualTo(500);
    }

    @Test
    void deveRetornar400ComACausaQuandoCorpoForIlegivel() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Invalid UTF-8 middle byte 0x64",
                mock(org.springframework.http.HttpInputMessage.class));

        ProblemDetail problema = handler.handleCorpoIlegivel(ex);

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getDetail()).contains("Invalid UTF-8 middle byte 0x64");
    }

    @Test
    void deveRetornar422ComOsTiposDisponiveisQuandoProjetoNaoTemSubtarefa() {
        SubtaskIssueTypeNotFoundException ex =
                new SubtaskIssueTypeNotFoundException("KAN", List.of("Epic", "Tarefa"));

        ProblemDetail problema = handler.handleTipoDeSubtarefaNaoEncontrado(ex);

        assertThat(problema.getStatus()).isEqualTo(422);
        assertThat(problema.getProperties()).containsEntry("tiposDisponiveis", List.of("Epic", "Tarefa"));
    }
}
