package com.juditecompany.jiramaster.exception;

import org.springframework.http.HttpStatusCode;

public class JiraApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String jiraBody;

    public JiraApiException(HttpStatusCode status, String jiraBody) {
        super("Jira retornou status " + status.value() + ": " + jiraBody);
        this.status = status;
        this.jiraBody = jiraBody;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getJiraBody() {
        return jiraBody;
    }
}
