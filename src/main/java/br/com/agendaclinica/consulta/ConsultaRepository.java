package br.com.agendaclinica.consulta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JpaSpecificationExecutor permite montar filtros opcionais em tempo de execução
 * (ver ConsultaSpecs). Consultas CANCELADA nunca ocupam horário.
 */
public interface ConsultaRepository extends JpaRepository<Consulta, Long>, JpaSpecificationExecutor<Consulta> {

    /** Dois intervalos se sobrepõem quando um começa antes de o outro terminar, e vice-versa. */
    @Query("""
            select count(c) > 0 from Consulta c
            where c.profissional.id = :profissionalId
              and c.status <> br.com.agendaclinica.consulta.StatusConsulta.CANCELADA
              and c.inicio < :fim
              and c.fim > :inicio
            """)
    boolean existeConflitoDoProfissional(@Param("profissionalId") Long profissionalId,
                                         @Param("inicio") LocalDateTime inicio,
                                         @Param("fim") LocalDateTime fim);

    @Query("""
            select count(c) > 0 from Consulta c
            where c.paciente.id = :pacienteId
              and c.profissional.id = :profissionalId
              and c.status <> br.com.agendaclinica.consulta.StatusConsulta.CANCELADA
              and c.inicio >= :diaInicio
              and c.inicio < :diaFim
            """)
    boolean existeConsultaDoPacienteNoDia(@Param("pacienteId") Long pacienteId,
                                          @Param("profissionalId") Long profissionalId,
                                          @Param("diaInicio") LocalDateTime diaInicio,
                                          @Param("diaFim") LocalDateTime diaFim);

    @Query("""
            select c from Consulta c
            where c.profissional.id = :profissionalId
              and c.status <> br.com.agendaclinica.consulta.StatusConsulta.CANCELADA
              and c.inicio >= :diaInicio
              and c.inicio < :diaFim
            order by c.inicio
            """)
    List<Consulta> findAgendaDoDia(@Param("profissionalId") Long profissionalId,
                                   @Param("diaInicio") LocalDateTime diaInicio,
                                   @Param("diaFim") LocalDateTime diaFim);
}
