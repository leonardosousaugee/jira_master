package com.juditecompany.jiramaster.dto.response;

import java.time.Instant;

public record ComentarioResponse(String id, String autor, String corpo, Instant criadoEm) {
}
