package br.com.agendaclinica.paciente;

import br.com.agendaclinica.common.ConflictException;
import br.com.agendaclinica.common.CpfValidator;
import br.com.agendaclinica.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de negócio de pacientes. O controller só recebe e devolve; a lógica fica aqui. */
@Service
public class PacienteService {

    private final PacienteRepository repository;

    public PacienteService(PacienteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Paciente criar(PacienteRequest req) {
        String cpf = CpfValidator.onlyDigits(req.cpf());
        if (repository.existsByCpf(cpf)) {
            throw new ConflictException("Já existe um paciente com este CPF");
        }
        Paciente paciente = new Paciente();
        aplicar(paciente, req, cpf);
        return repository.save(paciente);
    }

    @Transactional
    public Paciente atualizar(Long id, PacienteRequest req) {
        Paciente paciente = buscar(id);
        String cpf = CpfValidator.onlyDigits(req.cpf());
        if (repository.existsByCpfAndIdNot(cpf, id)) {
            throw new ConflictException("Já existe outro paciente com este CPF");
        }
        aplicar(paciente, req, cpf);
        return repository.save(paciente);
    }

    @Transactional(readOnly = true)
    public Paciente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Paciente> listar(String nome, Pageable pageable) {
        if (nome == null || nome.isBlank()) {
            return repository.findByAtivoTrue(pageable);
        }
        return repository.findByAtivoTrueAndNomeContainingIgnoreCase(nome.trim(), pageable);
    }

    /** Exclusão lógica: o registro fica no banco com ativo = false (histórico de consultas preservado). */
    @Transactional
    public void inativar(Long id) {
        Paciente paciente = buscar(id);
        paciente.setAtivo(false);
        repository.save(paciente);
    }

    private void aplicar(Paciente paciente, PacienteRequest req, String cpf) {
        paciente.setNome(req.nome().trim());
        paciente.setCpf(cpf);
        paciente.setDataNascimento(req.dataNascimento());
        paciente.setTelefone(req.telefone());
        paciente.setEmail(req.email());
    }
}
