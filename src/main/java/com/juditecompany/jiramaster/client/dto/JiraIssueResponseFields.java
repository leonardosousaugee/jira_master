package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueResponseFields(
        String summary,
        JsonNode description,
        JiraStatusDto status,
        JiraNameRef priority,
        JiraNameRef issuetype,
        JiraFieldRef project,
        JiraFieldRef parent,
        String created,
        String updated
) {
}
