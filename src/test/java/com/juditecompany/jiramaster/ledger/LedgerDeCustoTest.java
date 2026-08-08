package com.juditecompany.jiramaster.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerDeCustoTest {

    private final LedgerDeCusto ledger = new LedgerDeCusto(new ObjectMapper());

    private LinhaCusto linha(String ts, String card) {
        return new LinhaCusto(ts, card, 100, 20, new BigDecimal("0.0500"));
    }

    @Test
    void deveCriarBlocoQuandoDescricaoEstaVazia() {
        String resultado = ledger.acrescentar("", linha("2026-08-08T10:00", "KAN-1"));

        assertThat(ledger.ler(resultado).linhas()).hasSize(1);
    }

    @Test
    void devePreservarTextoHumanoAoCriarOBloco() {
        String resultado = ledger.acrescentar("Descricao do card", linha("2026-08-08T10:00", "KAN-1"));

        assertThat(ledger.separar(resultado).antes()).contains("Descricao do card");
        assertThat(ledger.ler(resultado).linhas()).hasSize(1);
    }

    @Test
    void devePreservarTextoHumanoIntactoAoLongoDeDezEscritas() {
        String humano = "Primeira linha\n\nTerceira linha com acento: analise";
        String descricao = humano;
        for (int i = 0; i < 10; i++) {
            descricao = ledger.acrescentar(descricao, linha("2026-08-08T10:0" + i, "KAN-1"));
        }

        assertThat(ledger.separar(descricao).antes().strip()).isEqualTo(humano);
        assertThat(ledger.ler(descricao).linhas()).hasSize(10);
    }

    @Test
    void deveAcrescentarSemPerderAsLinhasAnteriores() {
        String descricao = ledger.acrescentar("", linha("2026-08-08T10:00", "KAN-1"));
        descricao = ledger.acrescentar(descricao, linha("2026-08-08T11:00", "KAN-2"));

        assertThat(ledger.ler(descricao).linhas())
                .extracting(LinhaCusto::card).containsExactly("KAN-1", "KAN-2");
    }

    @Test
    void deveIgnorarEContarLinhaMalformadaSemDerrubarOResto() {
        String descricao = LedgerDeCusto.ABERTURA + "\n"
                + "{\"ts\":\"2026-08-08T10:00\",\"card\":\"KAN-1\",\"in\":100,\"out\":20,\"usd\":0.05}\n"
                + "isto nao e json\n"
                + "{\"ts\":\"2026-08-08T11:00\",\"card\":\"KAN-2\",\"in\":100,\"out\":20,\"usd\":0.05}\n"
                + LedgerDeCusto.FECHAMENTO;

        LedgerDeCusto.Leitura leitura = ledger.ler(descricao);

        assertThat(leitura.linhas()).hasSize(2);
        assertThat(leitura.linhasDescartadas()).isEqualTo(1);
        assertThat(leitura.total()).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void deveTratarAberturaSemFechamentoComoAusenciaDeBloco() {
        String descricao = "Texto\n" + LedgerDeCusto.ABERTURA + "\n{\"ts\":\"x\"}";

        assertThat(ledger.separar(descricao).temBloco()).isFalse();
        assertThat(ledger.ler(descricao).linhas()).isEmpty();
    }

    @Test
    void deveUsarAPrimeiraAberturaQuandoHouverDuas() {
        String descricao = LedgerDeCusto.ABERTURA + "\n"
                + "{\"ts\":\"2026-08-08T10:00\",\"card\":\"KAN-1\",\"in\":1,\"out\":1,\"usd\":0.01}\n"
                + LedgerDeCusto.FECHAMENTO + "\n" + LedgerDeCusto.ABERTURA + "\nlixo\n" + LedgerDeCusto.FECHAMENTO;

        LedgerDeCusto.Leitura leitura = ledger.ler(descricao);

        assertThat(leitura.linhas()).hasSize(1);
        assertThat(leitura.linhas().get(0).card()).isEqualTo("KAN-1");
    }

    @Test
    void deveDevolverLeituraVaziaQuandoNaoHaBloco() {
        LedgerDeCusto.Leitura leitura = ledger.ler("So texto humano");

        assertThat(leitura.vazio()).isTrue();
        assertThat(leitura.linhas()).isEmpty();
    }

    @Test
    void devePreservarTextoDepoisDoBloco() {
        String descricao = "Antes\n\n" + LedgerDeCusto.ABERTURA + "\n"
                + "{\"ts\":\"2026-08-08T10:00\",\"card\":\"KAN-1\",\"in\":1,\"out\":1,\"usd\":0.01}\n"
                + LedgerDeCusto.FECHAMENTO + "\nDepois";

        String resultado = ledger.acrescentar(descricao, linha("2026-08-08T12:00", "KAN-2"));

        assertThat(resultado).contains("Antes").contains("Depois");
        assertThat(ledger.ler(resultado).linhas()).hasSize(2);
    }
}
