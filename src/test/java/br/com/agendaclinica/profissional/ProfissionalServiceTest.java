package br.com.agendaclinica.profissional;

import br.com.agendaclinica.common.ConflictException;
import br.com.agendaclinica.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfissionalServiceTest {

    @Mock
    ProfissionalRepository repository;

    @InjectMocks
    ProfissionalService service;

    private static ProfissionalRequest request(Integer duracao) {
        return new ProfissionalRequest(" Dra. Ana Lima ", " CRM-SP 123456 ", Especialidade.CARDIOLOGIA, duracao);
    }

    @Test
    void criaProfissionalComDuracaoInformada() {
        when(repository.existsByRegistroConselho("CRM-SP 123456")).thenReturn(false);
        when(repository.save(any(Profissional.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(request(45));

        ArgumentCaptor<Profissional> captor = ArgumentCaptor.forClass(Profissional.class);
        verify(repository).save(captor.capture());
        assertEquals("Dra. Ana Lima", captor.getValue().getNome());
        assertEquals("CRM-SP 123456", captor.getValue().getRegistroConselho());
        assertEquals(Especialidade.CARDIOLOGIA, captor.getValue().getEspecialidade());
        assertEquals(45, captor.getValue().getDuracaoConsultaMin());
    }

    @Test
    void usaDuracaoPadraoDe30MinutosQuandoNaoInformada() {
        when(repository.save(any(Profissional.class))).thenAnswer(inv -> inv.getArgument(0));

        Profissional criado = service.criar(request(null));

        assertEquals(30, criado.getDuracaoConsultaMin());
    }

    @Test
    void naoCriaComRegistroDuplicado() {
        when(repository.existsByRegistroConselho("CRM-SP 123456")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.criar(request(30)));
        verify(repository, never()).save(any());
    }

    @Test
    void buscaInexistenteLancaNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.buscar(7L));
    }

    @Test
    void atualizaProfissional() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Profissional()));
        when(repository.existsByRegistroConselhoAndIdNot("CRM-SP 123456", 1L)).thenReturn(false);
        when(repository.save(any(Profissional.class))).thenAnswer(inv -> inv.getArgument(0));

        Profissional atualizado = service.atualizar(1L, request(20));

        assertEquals("Dra. Ana Lima", atualizado.getNome());
        assertEquals(20, atualizado.getDuracaoConsultaMin());
    }

    @Test
    void naoAtualizaParaRegistroDeOutroProfissional() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Profissional()));
        when(repository.existsByRegistroConselhoAndIdNot("CRM-SP 123456", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.atualizar(1L, request(30)));
        verify(repository, never()).save(any());
    }

    @Test
    void inativarMarcaAtivoComoFalsoSemApagar() {
        Profissional profissional = new Profissional();
        when(repository.findById(1L)).thenReturn(Optional.of(profissional));

        service.inativar(1L);

        assertFalse(profissional.isAtivo());
        verify(repository).save(profissional);
        verify(repository, never()).delete(any());
    }

    @Test
    void listaSemFiltroUsaSoOsAtivos() {
        var pageable = PageRequest.of(0, 20);

        service.listar(null, pageable);

        verify(repository).findByAtivoTrue(pageable);
    }

    @Test
    void listaComEspecialidadeFiltraEntreOsAtivos() {
        var pageable = PageRequest.of(0, 20);

        service.listar(Especialidade.PEDIATRIA, pageable);

        verify(repository).findByAtivoTrueAndEspecialidade(Especialidade.PEDIATRIA, pageable);
    }
}
