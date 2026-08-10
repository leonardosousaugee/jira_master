package com.juditecompany.jiramaster.dto.request;

/**
 * Campo nulo nao e tocado — vale para {@code titulo}, {@code descricao} e {@code worker}. Para
 * limpar o Worker, mande string vazia.
 */
public record EditarCardRequest(String titulo, String descricao, String worker) {
}
