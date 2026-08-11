package com.juditecompany.jiramaster.exception;

/**
 * A chave do projeto entra concatenada na JQL. Sem conferir o formato antes, um valor como
 * {@code KAN OR project is not null} vira clausula e a busca devolve issues de todo projeto que o
 * token enxerga — controle de acesso quebrado, nao erro de digitacao.
 */
public class ProjectKeyInvalidoException extends RuntimeException {

    public ProjectKeyInvalidoException(String projectKey) {
        super("projectKey invalido: '" + projectKey + "'. "
                + "Use apenas letras maiusculas e digitos, comecando por letra.");
    }
}
