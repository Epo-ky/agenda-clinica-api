package br.com.agendaclinica.profissional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    boolean existsByRegistroConselho(String registroConselho);

    boolean existsByRegistroConselhoAndIdNot(String registroConselho, Long id);

    Page<Profissional> findByAtivoTrue(Pageable pageable);

    Page<Profissional> findByAtivoTrueAndEspecialidade(Especialidade especialidade, Pageable pageable);
}
