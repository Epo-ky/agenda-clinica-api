package br.com.agendaclinica.paciente;

import br.com.agendaclinica.common.ConflictException;
import br.com.agendaclinica.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Teste unitário: o repository é um mock (Mockito), sem banco nem Spring. Parecido com Moq no .NET. */
@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    PacienteRepository repository;

    @InjectMocks
    PacienteService service;

    private static PacienteRequest request(String cpf) {
        return new PacienteRequest("  Maria Silva ", cpf, LocalDate.of(1990, 5, 10),
                "11999990000", "maria@email.com");
    }

    @Test
    void criaPacienteGuardandoCpfSoComNumerosENomeSemEspacos() {
        when(repository.existsByCpf("52998224725")).thenReturn(false);
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(request("529.982.247-25"));

        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(captor.capture());
        assertEquals("52998224725", captor.getValue().getCpf());
        assertEquals("Maria Silva", captor.getValue().getNome());
        assertEquals(true, captor.getValue().isAtivo());
    }

    @Test
    void naoCriaPacienteComCpfDuplicado() {
        when(repository.existsByCpf("52998224725")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.criar(request("52998224725")));
        verify(repository, never()).save(any());
    }

    @Test
    void buscaInexistenteLancaNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.buscar(99L));
    }

    @Test
    void atualizaPaciente() {
        Paciente existente = new Paciente();
        existente.setNome("Antigo");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.existsByCpfAndIdNot("52998224725", 1L)).thenReturn(false);
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        Paciente atualizado = service.atualizar(1L, request("52998224725"));

        assertEquals("Maria Silva", atualizado.getNome());
        assertEquals("52998224725", atualizado.getCpf());
    }

    @Test
    void naoAtualizaParaCpfDeOutroPaciente() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Paciente()));
        when(repository.existsByCpfAndIdNot("52998224725", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.atualizar(1L, request("52998224725")));
        verify(repository, never()).save(any());
    }

    @Test
    void atualizarInexistenteLancaNotFound() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.atualizar(5L, request("52998224725")));
    }

    @Test
    void inativarMarcaAtivoComoFalsoSemApagar() {
        Paciente paciente = new Paciente();
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));

        service.inativar(1L);

        assertFalse(paciente.isAtivo());
        verify(repository).save(paciente);
        verify(repository, never()).delete(any());
    }

    @Test
    void listaSemFiltroUsaSoOsAtivos() {
        var pageable = PageRequest.of(0, 20);

        service.listar("  ", pageable);

        verify(repository).findByAtivoTrue(pageable);
    }

    @Test
    void listaComFiltroBuscaPorNomeEntreOsAtivos() {
        var pageable = PageRequest.of(0, 20);

        service.listar(" mar ", pageable);

        verify(repository).findByAtivoTrueAndNomeContainingIgnoreCase("mar", pageable);
    }
}
