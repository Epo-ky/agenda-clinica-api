package br.com.agendaclinica.consulta;

import org.junit.jupiter.api.Test;

import static br.com.agendaclinica.consulta.StatusConsulta.AGENDADA;
import static br.com.agendaclinica.consulta.StatusConsulta.CANCELADA;
import static br.com.agendaclinica.consulta.StatusConsulta.CONFIRMADA;
import static br.com.agendaclinica.consulta.StatusConsulta.REALIZADA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testa a matriz inteira de transições: 4 origens x 4 destinos = 16 combinações. */
class StatusConsultaTest {

    @Test
    void transicoesPermitidas() {
        assertTrue(AGENDADA.podeIrPara(CONFIRMADA));
        assertTrue(AGENDADA.podeIrPara(CANCELADA));
        assertTrue(CONFIRMADA.podeIrPara(REALIZADA));
        assertTrue(CONFIRMADA.podeIrPara(CANCELADA));
    }

    @Test
    void agendadaNaoPulaDireitoParaRealizada() {
        assertFalse(AGENDADA.podeIrPara(REALIZADA));
    }

    @Test
    void nenhumStatusVoltaParaAgendadaOuParaSiMesmo() {
        for (StatusConsulta s : StatusConsulta.values()) {
            assertFalse(s.podeIrPara(AGENDADA), s + " -> AGENDADA");
            assertFalse(s.podeIrPara(s), s + " -> " + s);
        }
    }

    @Test
    void confirmadaNaoVoltaParaAgendada() {
        assertFalse(CONFIRMADA.podeIrPara(AGENDADA));
    }

    @Test
    void estadosFinaisNaoSaemDeLa() {
        for (StatusConsulta destino : StatusConsulta.values()) {
            assertFalse(REALIZADA.podeIrPara(destino), "REALIZADA -> " + destino);
            assertFalse(CANCELADA.podeIrPara(destino), "CANCELADA -> " + destino);
        }
    }

    @Test
    void ExatamenteQuatroTransicoesSaoPermitidas() {
        long total = 0;
        for (StatusConsulta origem : StatusConsulta.values()) {
            for (StatusConsulta destino : StatusConsulta.values()) {
                if (origem.podeIrPara(destino)) {
                    total++;
                }
            }
        }
        assertEquals(4, total);
    }
}
