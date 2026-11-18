package br.com.agendaclinica.paciente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sem SQL escrito à mão: o Spring Data gera a query a partir do nome do método
 * (parecido com o LINQ do EF Core, mas definido pelo nome).
 */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    Page<Paciente> findByAtivoTrue(Pageable pageable);

    Page<Paciente> findByAtivoTrueAndNomeContainingIgnoreCase(String nome, Pageable pageable);
}
