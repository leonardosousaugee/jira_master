package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraNameRef(String id, String name) {

    public JiraNameRef(String name) {
        this(null, name);
    }

    public static JiraNameRef porId(String id) {
        return new JiraNameRef(id, null);
    }
}
