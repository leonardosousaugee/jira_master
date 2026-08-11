package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.Pattern;

/**
 * Campo nulo nao e tocado — vale para {@code titulo}, {@code descricao} e {@code worker}. Para
 * limpar o Worker, mande string vazia.
 *
 * <p>{@code titulo} nao leva {@code @NotBlank} porque nulo aqui e valido (significa "nao mexa").
 * O que nao vale e mandar o campo em branco: isso nao apaga titulo nenhum, so viaja ate o Jira
 * para voltar como erro remoto opaco.
 */
public record EditarCardRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "titulo, quando informado, nao pode ser vazio") String titulo,
        String descricao,
        String worker) {
}
