package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;
import com.juditecompany.jiramaster.ledger.LinhaCusto;

import java.util.List;

public interface JiraCardService {

    CardResponse criarCard(CriarCardRequest request);

    List<CardResumoResponse> lerCardsEmAberto(String projectKeyOverride);

    CardResponse buscarCardPorId(String issueKey);

    CardResponse editarCard(String issueKey, EditarCardRequest request);

    List<TransicaoResponse> listarTransicoesDisponiveis(String issueKey);

    void alterarEtapaCard(String issueKey, AlterarEtapaRequest request);

    CardResponse adicionarSubCard(String issueKeyPai, AdicionarSubCardRequest request);

    void editarPrioridade(String issueKey, EditarPrioridadeRequest request);

    ComentarioResponse adicionarComentario(String issueKey, AdicionarComentarioRequest request);

    List<ComentarioResponse> listarComentarios(String issueKey);

    LinhaCusto registrarCusto(String issueKey, RegistrarCustoRequest request);

    List<CardResumoResponse> listarSubCards(String issueKey);

    CustoArvoreResponse lerCustoDaArvore(String issueKey);

    LinhaCusto estornarCusto(String issueKey, EstornarCustoRequest request);

    ResultadoHoldResponse moverArvoreParaHold(String issueKey);
}
