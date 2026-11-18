package br.com.agendaclinica.usuario;

import br.com.agendaclinica.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    UsuarioRepository repository;

    @Spy
    PasswordEncoder encoder = new BCryptPasswordEncoder();

    @InjectMocks
    UsuarioService service;

    @Test
    void criaUsuarioGuardandoSoOHashDaSenhaEEmailNormalizado() {
        when(repository.existsByEmail("ana@clinica.com")).thenReturn(false);
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(new UsuarioRequest("  Ana@Clinica.COM ", "senha-forte-123", Perfil.RECEPCAO));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        Usuario salvo = captor.getValue();
        assertEquals("ana@clinica.com", salvo.getEmail());
        assertEquals(Perfil.RECEPCAO, salvo.getPerfil());
        assertTrue(salvo.isAtivo());
        assertNotEquals("senha-forte-123", salvo.getSenhaHash());
        assertTrue(encoder.matches("senha-forte-123", salvo.getSenhaHash()));
    }

    @Test
    void naoCriaUsuarioComEmailJaCadastrado() {
        when(repository.existsByEmail("ana@clinica.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.criar(new UsuarioRequest("ana@clinica.com", "senha-forte-123", Perfil.ADMIN)));
        verify(repository, never()).save(any());
    }

    @Test
    void duasSenhasIguaisGeramHashesDiferentes() {
        // BCrypt usa um "sal" aleatório: vazar o hash de um usuário não revela quem tem a mesma senha.
        assertNotEquals(encoder.encode("mesma-senha"), encoder.encode("mesma-senha"));
    }
}
