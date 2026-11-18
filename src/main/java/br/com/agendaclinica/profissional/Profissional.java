package br.com.agendaclinica.profissional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profissionais")
public class Profissional {

    /** Duração padrão de uma consulta, em minutos, quando não informada. */
    public static final int DURACAO_PADRAO_MIN = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false, unique = true, length = 30)
    private String registroConselho;

    // EnumType.STRING grava o nome ("CARDIOLOGIA"). Nunca use ORDINAL: reordenar o enum quebraria os dados.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Especialidade especialidade;

    @Column(nullable = false)
    private int duracaoConsultaMin = DURACAO_PADRAO_MIN;

    @Column(nullable = false)
    private boolean ativo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getRegistroConselho() { return registroConselho; }
    public void setRegistroConselho(String registroConselho) { this.registroConselho = registroConselho; }

    public Especialidade getEspecialidade() { return especialidade; }
    public void setEspecialidade(Especialidade especialidade) { this.especialidade = especialidade; }

    public int getDuracaoConsultaMin() { return duracaoConsultaMin; }
    public void setDuracaoConsultaMin(int duracaoConsultaMin) { this.duracaoConsultaMin = duracaoConsultaMin; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
