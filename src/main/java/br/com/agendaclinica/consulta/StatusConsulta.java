package br.com.agendaclinica.consulta;

import java.util.Set;

public enum StatusConsulta {
    AGENDADA,
    CONFIRMADA,
    REALIZADA,
    CANCELADA;

    /**
     * Regra 6: AGENDADA -> CONFIRMADA -> REALIZADA; AGENDADA ou CONFIRMADA -> CANCELADA.
     * REALIZADA e CANCELADA são estados finais. O switch devolve o conjunto de destinos permitidos.
     */
    private Set<StatusConsulta> destinosPermitidos() {
        return switch (this) {
            case AGENDADA -> Set.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> Set.of(REALIZADA, CANCELADA);
            case REALIZADA, CANCELADA -> Set.of();
        };
    }

    public boolean podeIrPara(StatusConsulta destino) {
        return destinosPermitidos().contains(destino);
    }
}
