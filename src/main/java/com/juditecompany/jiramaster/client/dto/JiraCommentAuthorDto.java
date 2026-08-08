package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCommentAuthorDto(String displayName) {
}
