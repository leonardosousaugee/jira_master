package com.juditecompany.jiramaster.exception;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(String issueKey) {
        super("Card nao encontrado: " + issueKey);
    }
}
