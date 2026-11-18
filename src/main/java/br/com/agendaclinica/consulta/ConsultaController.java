package br.com.agendaclinica.consulta;

import br.com.agendaclinica.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ConsultaController {

    private final ConsultaService service;

    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    @PostMapping("/consultas")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaResponse agendar(@Valid @RequestBody ConsultaRequest req) {
        return service.agendar(req);
    }

    @GetMapping("/consultas")
    public PageResponse<ConsultaResponse> listar(
            @RequestParam(required = false) Long profissionalId,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) StatusConsulta status,
            @PageableDefault(size = 20, sort = "inicio") Pageable pageable) {
        return PageResponse.from(service.listar(profissionalId, pacienteId, data, status, pageable), c -> c);
    }

    @GetMapping("/profissionais/{id}/horarios-livres")
    public HorariosLivresResponse horariosLivres(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.horariosLivres(id, data);
    }
}
