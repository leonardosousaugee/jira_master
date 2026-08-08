package com.juditecompany.jiramaster.client.dto;

import java.util.List;

public record JiraProjectDto(String id, String key, List<JiraIssueTypeDto> issueTypes) {
}
