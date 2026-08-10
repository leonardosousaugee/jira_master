package com.juditecompany.jiramaster.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CardNotFoundException.class)
    public ProblemDetail handleCardNaoEncontrado(CardNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransitionNotFoundException.class)
    public ProblemDetail handleTransicaoNaoEncontrada(TransitionNotFoundException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setProperty("transicoesDisponiveis", ex.getTransicoesDisponiveis());
        return problema;
    }

    @ExceptionHandler(JiraApiException.class)
    public ProblemDetail handleJiraApiException(JiraApiException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(ex.getStatus().value()), ex.getMessage());
    }

    @ExceptionHandler(LinhaDeCustoNaoEncontradaException.class)
    public ProblemDetail handleLinhaDeCustoNaoEncontrada(LinhaDeCustoNaoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ModeloDesconhecidoException.class)
    public ProblemDetail handleModeloDesconhecido(ModeloDesconhecidoException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problema.setProperty("modelosConhecidos", ex.getModelosConhecidos());
        return problema;
    }

    @ExceptionHandler(SubtaskIssueTypeNotFoundException.class)
    public ProblemDetail handleTipoDeSubtarefaNaoEncontrado(SubtaskIssueTypeNotFoundException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problema.setProperty("tiposDisponiveis", ex.getTiposDisponiveis());
        return problema;
    }

    @ExceptionHandler(CampoWorkerNaoDisponivelException.class)
    public ProblemDetail handleCampoWorkerNaoDisponivel(CampoWorkerNaoDisponivelException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /**
     * Sem este handler qualquer corpo malformado cai no handler generico e vira um 500 mudo,
     * escondendo a causa real (encoding errado, JSON quebrado, tipo incompativel).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        log.warn("Corpo da requisicao ilegivel: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisicao ilegivel: " + ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidacao(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::toString)
                .collect(Collectors.toList());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisicao invalida");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleErroGenerico(Exception ex) {
        log.error("Erro interno inesperado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }
}
