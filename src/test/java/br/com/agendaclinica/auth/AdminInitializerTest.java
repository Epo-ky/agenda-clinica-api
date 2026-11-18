package br.com.agendaclinica.auth;

import br.com.agendaclinica.usuario.Perfil;
import br.com.agendaclinica.usuario.UsuarioRepository;
import br.com.agendaclinica.usuario.UsuarioRequest;
import br.com.agendaclinica.usuario.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    UsuarioRepository repository;
    @Mock
    UsuarioService usuarioService;

    private AdminInitializer inicializador(String senha) {
        return new AdminInitializer(repository, usuarioService, "admin@clinica.com", senha);
    }

    @Test
    void criaOAdminQuandoNaoExisteNenhum() {
        when(repository.existsByPerfil(Perfil.ADMIN)).thenReturn(false);

        inicializador("senha-do-admin-123").run(null);

        verify(usuarioService).criar(new UsuarioRequest("admin@clinica.com", "senha-do-admin-123", Perfil.ADMIN));
    }

    @Test
    void naoMexeSeJaExisteUmAdmin() {
        when(repository.existsByPerfil(Perfil.ADMIN)).thenReturn(true);

        inicializador("senha-do-admin-123").run(null);

        verify(usuarioService, never()).criar(any());
    }

    @Test
    void semSenhaNaoCriaNadaENaoQuebraASubida() {
        when(repository.existsByPerfil(Perfil.ADMIN)).thenReturn(false);

        inicializador("").run(null);

        verify(usuarioService, never()).criar(any());
    }

    @Test
    void senhaCurtaImpedeASubida() {
        when(repository.existsByPerfil(Perfil.ADMIN)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> inicializador("curta").run(null));
        verify(usuarioService, never()).criar(any());
    }
}
