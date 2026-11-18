package br.com.agendaclinica.profissional;

public record ProfissionalResponse(
        Long id,
        String nome,
        String registroConselho,
        Especialidade especialidade,
        int duracaoConsultaMin,
        boolean ativo
) {

    public static ProfissionalResponse from(Profissional p) {
        return new ProfissionalResponse(
                p.getId(), p.getNome(), p.getRegistroConselho(),
                p.getEspecialidade(), p.getDuracaoConsultaMin(), p.isAtivo());
    }
}
