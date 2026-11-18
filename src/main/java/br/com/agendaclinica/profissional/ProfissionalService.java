package br.com.agendaclinica.profissional;

import br.com.agendaclinica.common.ConflictException;
import br.com.agendaclinica.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfissionalService {

    private final ProfissionalRepository repository;

    public ProfissionalService(ProfissionalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Profissional criar(ProfissionalRequest req) {
        String registro = req.registroConselho().trim();
        if (repository.existsByRegistroConselho(registro)) {
            throw new ConflictException("Já existe um profissional com este registro no conselho");
        }
        Profissional profissional = new Profissional();
        aplicar(profissional, req, registro);
        return repository.save(profissional);
    }

    @Transactional
    public Profissional atualizar(Long id, ProfissionalRequest req) {
        Profissional profissional = buscar(id);
        String registro = req.registroConselho().trim();
        if (repository.existsByRegistroConselhoAndIdNot(registro, id)) {
            throw new ConflictException("Já existe outro profissional com este registro no conselho");
        }
        aplicar(profissional, req, registro);
        return repository.save(profissional);
    }

    @Transactional(readOnly = true)
    public Profissional buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Profissional não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Profissional> listar(Especialidade especialidade, Pageable pageable) {
        if (especialidade == null) {
            return repository.findByAtivoTrue(pageable);
        }
        return repository.findByAtivoTrueAndEspecialidade(especialidade, pageable);
    }

    /** Exclusão lógica, como em pacientes: o histórico de consultas continua apontando para ele. */
    @Transactional
    public void inativar(Long id) {
        Profissional profissional = buscar(id);
        profissional.setAtivo(false);
        repository.save(profissional);
    }

    private void aplicar(Profissional profissional, ProfissionalRequest req, String registro) {
        profissional.setNome(req.nome().trim());
        profissional.setRegistroConselho(registro);
        profissional.setEspecialidade(req.especialidade());
        profissional.setDuracaoConsultaMin(
                req.duracaoConsultaMin() != null ? req.duracaoConsultaMin() : Profissional.DURACAO_PADRAO_MIN);
    }
}
