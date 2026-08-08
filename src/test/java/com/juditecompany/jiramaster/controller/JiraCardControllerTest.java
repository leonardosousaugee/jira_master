package com.juditecompany.jiramaster.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juditecompany.jiramaster.dto.request.AlterarEtapaRequest;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.service.JiraCardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JiraCardController.class)
class JiraCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JiraCardService service;

    @Test
    void deveCriarCardERetornar201() throws Exception {
        CardResponse resposta = new CardResponse("KAN-1", "Titulo", "Descricao", "To Do", "Medium", "Task", "KAN",
                Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:00:00Z"));
        when(service.criarCard(any())).thenReturn(resposta);

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Titulo","descricao":"Descricao"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueKey").value("KAN-1"));
    }

    @Test
    void deveRetornar400QuandoTituloEstiverAusente() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveBuscarCardPorIdERetornar200() throws Exception {
        CardResponse resposta = new CardResponse("KAN-1", "Titulo", "Descricao", "To Do", "Medium", "Task", "KAN",
                Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:00:00Z"));
        when(service.buscarCardPorId("KAN-1")).thenReturn(resposta);

        mockMvc.perform(get("/api/cards/KAN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo"));
    }

    @Test
    void deveAlterarEtapaERetornar204() throws Exception {
        mockMvc.perform(post("/api/cards/KAN-1/etapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etapaDestino":"In Progress"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void deveRetornar400QuandoEtapaNaoExistir() throws Exception {
        org.mockito.Mockito.doThrow(new TransitionNotFoundException("KAN-1", "Bloqueado", List.of("To Do", "Done")))
                .when(service).alterarEtapaCard(eq("KAN-1"), any());

        mockMvc.perform(post("/api/cards/KAN-1/etapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etapaDestino":"Bloqueado"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveDecodificarCorpoUtf8AcentuadoQuandoContentTypeNaoTrazCharset() throws Exception {
        mockMvc.perform(post("/api/cards/KAN-1/etapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapaDestino\":\"Concluído\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<AlterarEtapaRequest> captor = ArgumentCaptor.forClass(AlterarEtapaRequest.class);
        verify(service).alterarEtapaCard(eq("KAN-1"), captor.capture());
        assertThat(captor.getValue().etapaDestino()).isEqualTo("Concluído");
    }

    @Test
    void deveRetornar400ComACausaQuandoCorpoNaoForJsonLegivel() throws Exception {
        byte[] corpoLatin1 = new byte[]{'{', '"', 'e', 't', 'a', 'p', 'a', 'D', 'e', 's', 't', 'i', 'n', 'o', '"', ':',
                '"', 'C', 'o', 'n', 'c', 'l', 'u', (byte) 0xED, 'd', 'o', '"', '}'};

        mockMvc.perform(post("/api/cards/KAN-1/etapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLatin1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("UTF-8")));

        verify(service, org.mockito.Mockito.never()).alterarEtapaCard(eq("KAN-1"), any());
    }
}
