package com.juditecompany.jiramaster.controller;

import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;
import com.juditecompany.jiramaster.ledger.LinhaCusto;
import com.juditecompany.jiramaster.service.JiraCardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "Operacoes sobre cards (issues) do Jira")
public class JiraCardController {

    private final JiraCardService service;

    public JiraCardController(JiraCardService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CardResponse> criarCard(@Valid @RequestBody CriarCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarCard(request));
    }

    @GetMapping("/abertos")
    public ResponseEntity<List<CardResumoResponse>> lerCardsEmAberto(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(service.lerCardsEmAberto(projectKey));
    }

    @GetMapping("/{issueKey}")
    public ResponseEntity<CardResponse> buscarCardPorId(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.buscarCardPorId(issueKey));
    }

    @PatchMapping("/{issueKey}")
    public ResponseEntity<CardResponse> editarCard(@PathVariable String issueKey,
                                                     @Valid @RequestBody EditarCardRequest request) {
        return ResponseEntity.ok(service.editarCard(issueKey, request));
    }

    @GetMapping("/{issueKey}/transicoes")
    public ResponseEntity<List<TransicaoResponse>> listarTransicoesDisponiveis(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.listarTransicoesDisponiveis(issueKey));
    }

    @PostMapping("/{issueKey}/etapa")
    public ResponseEntity<Void> alterarEtapaCard(@PathVariable String issueKey,
                                                   @Valid @RequestBody AlterarEtapaRequest request) {
        service.alterarEtapaCard(issueKey, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{issueKey}/subcards")
    public ResponseEntity<CardResponse> adicionarSubCard(@PathVariable String issueKey,
                                                           @Valid @RequestBody AdicionarSubCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarSubCard(issueKey, request));
    }

    @PatchMapping("/{issueKey}/prioridade")
    public ResponseEntity<Void> editarPrioridade(@PathVariable String issueKey,
                                                   @Valid @RequestBody EditarPrioridadeRequest request) {
        service.editarPrioridade(issueKey, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{issueKey}/subcards")
    public ResponseEntity<List<CardResumoResponse>> listarSubCards(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.listarSubCards(issueKey));
    }

    @GetMapping("/{issueKey}/custo")
    public ResponseEntity<CustoArvoreResponse> lerCustoDaArvore(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.lerCustoDaArvore(issueKey));
    }

    @PostMapping("/{issueKey}/custos/estornos")
    public ResponseEntity<LinhaCusto> estornarCusto(@PathVariable String issueKey,
                                                      @Valid @RequestBody EstornarCustoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.estornarCusto(issueKey, request));
    }

    @PostMapping("/{issueKey}/hold")
    public ResponseEntity<ResultadoHoldResponse> moverArvoreParaHold(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.moverArvoreParaHold(issueKey));
    }

    @PostMapping("/{issueKey}/custos")
    public ResponseEntity<LinhaCusto> registrarCusto(@PathVariable String issueKey,
                                                       @Valid @RequestBody RegistrarCustoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrarCusto(issueKey, request));
    }

    @PostMapping("/{issueKey}/comentarios")
    public ResponseEntity<ComentarioResponse> adicionarComentario(@PathVariable String issueKey,
                                                                    @Valid @RequestBody AdicionarComentarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarComentario(issueKey, request));
    }
}
