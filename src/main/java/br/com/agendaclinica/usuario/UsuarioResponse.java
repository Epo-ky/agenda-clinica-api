package br.com.agendaclinica.usuario;

/** Nunca inclui a senha nem o hash. */
public record UsuarioResponse(Long id, String email, Perfil perfil, boolean ativo) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getPerfil(), u.isAtivo());
    }
}
