package br.com.agendaclinica.profissional;

import br.com.agendaclinica.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/profissionais")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')") // leitura: os dois perfis; escrita: só ADMIN (abaixo)
public class ProfissionalController {

    private final ProfissionalService service;

    public ProfissionalController(ProfissionalService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProfissionalResponse> listar(
            @RequestParam(required = false) Especialidade especialidade,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return PageResponse.from(service.listar(especialidade, pageable), ProfissionalResponse::from);
    }

    @GetMapping("/{id}")
    public ProfissionalResponse buscar(@PathVariable Long id) {
        return ProfissionalResponse.from(service.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfissionalResponse> criar(@Valid @RequestBody ProfissionalRequest req) {
        Profissional criado = service.criar(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.getId()).toUri();
        return ResponseEntity.created(location).body(ProfissionalResponse.from(criado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfissionalResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProfissionalRequest req) {
        return ProfissionalResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
