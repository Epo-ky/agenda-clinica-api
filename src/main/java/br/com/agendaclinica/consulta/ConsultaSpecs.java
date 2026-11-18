package br.com.agendaclinica.consulta;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Cada método devolve um filtro (um "Where") que só existe se o parâmetro vier preenchido.
 * Parecido com encadear .Where(...) condicionalmente num IQueryable do EF Core.
 */
final class ConsultaSpecs {

    private ConsultaSpecs() {
    }

    static Specification<Consulta> filtrar(Long profissionalId, Long pacienteId, LocalDate data, StatusConsulta status) {
        return Specification.where(profissional(profissionalId))
                .and(paciente(pacienteId))
                .and(data(data))
                .and(status(status));
    }

    private static Specification<Consulta> profissional(Long id) {
        return id == null ? null : (root, query, cb) -> cb.equal(root.get("profissional").get("id"), id);
    }

    private static Specification<Consulta> paciente(Long id) {
        return id == null ? null : (root, query, cb) -> cb.equal(root.get("paciente").get("id"), id);
    }

    private static Specification<Consulta> data(LocalDate data) {
        return data == null ? null : (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("inicio"), data.atStartOfDay()),
                cb.lessThan(root.get("inicio"), data.plusDays(1).atStartOfDay()));
    }

    private static Specification<Consulta> status(StatusConsulta status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
