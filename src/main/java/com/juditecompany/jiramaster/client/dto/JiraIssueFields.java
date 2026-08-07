package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraIssueFields(
        JiraFieldRef project,
        String summary,
        Object description,
        JiraNameRef issuetype,
        JiraFieldRef parent,
        JiraNameRef priority
) {
}
