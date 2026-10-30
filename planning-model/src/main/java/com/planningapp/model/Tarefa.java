package com.planningapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "tarefas",
        uniqueConstraints = @UniqueConstraint(name = "uk_tarefa_sala_numero", columnNames = {"id_sala", "numero"}))
public class Tarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String numero;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    /** Opcional; agrupa as estatísticas, como no Planning original. */
    @Column(length = 50)
    private String sprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTarefa status = StatusTarefa.FILA;

    /** Só tem sentido enquanto a tarefa está {@link StatusTarefa#LIBERADA}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FaseVotacao fase;

    /** Trilha de horas de teste; nula enquanto o moderador não liberar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fase_horas_teste", length = 20)
    private FaseHorasTeste faseHorasTeste;

    @Column(name = "rodada_atual", nullable = false)
    private int rodadaAtual = 1;

    @Column(name = "pontos_finais")
    private Integer pontosFinais;

    @Column(name = "horas_finais")
    private Integer horasFinais;

    @Column(name = "horas_teste_finais")
    private Integer horasTesteFinais;

    /** Quando a votação (da rodada que valeu) foi aberta. Com {@code encerradaEm}, dá a duração da votação. */
    @Column(name = "liberada_em")
    private Instant liberadaEm;

    /** Quando foi finalizada ou pulada; filtra as estatísticas por período. */
    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    /** Quando a votação de horas foi aberta: base para medir quanto cada um levou para votar nas horas. */
    @Column(name = "horas_liberadas_em")
    private Instant horasLiberadasEm;

    /**
     * Colunas do arquivo importado que não são campos próprios da tarefa, em JSON ({@code {"Prioridade":"Alta"}}).
     * O front as mostra como colunas dinâmicas, como no Planning original. A conversão fica no mapper.
     */
    @Column(name = "dados_extras", columnDefinition = "TEXT")
    private String dadosExtras;

    /**
     * Importada do Redmine: o número é o do chamado, e a estimativa volta para ele ao finalizar. As de CSV ficam
     * de fora, porque o número pode não ser um chamado.
     */
    @Column(name = "do_redmine", nullable = false)
    private boolean doRedmine;

    /** O objeto do chamado como veio do {@code /issues.json} do Redmine na importação, sem alteração. */
    @Column(name = "json_redmine", columnDefinition = "TEXT")
    private String jsonRedmine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sala", nullable = false)
    private Sala sala;

    protected Tarefa() {
        // exigido pelo JPA
    }

    public Tarefa(String numero, String titulo, String descricao) {
        this(numero, titulo, descricao, null);
    }

    public Tarefa(String numero, String titulo, String descricao, String sprint) {
        this.numero = numero;
        this.titulo = titulo;
        this.descricao = descricao;
        this.sprint = sprint;
    }

    /**
     * Abre a votação de pontos. As regras (só da fila ou pulada, uma por sala) ficam no service. Uma tarefa
     * pulada volta numa rodada nova, para os votos de antes ficarem como histórico e não se misturarem.
     */
    public void liberar() {
        if (status == StatusTarefa.PULADA) {
            rodadaAtual++;
        }
        status = StatusTarefa.LIBERADA;
        fase = FaseVotacao.PONTOS;
        liberadaEm = Instant.now();
        encerradaEm = null;
    }

    public void avancarPara(FaseVotacao novaFase) {
        fase = novaFase;
        if (novaFase == FaseVotacao.HORAS) {
            horasLiberadasEm = Instant.now();
        }
    }

    public void definirDadosExtras(String json) {
        dadosExtras = json;
    }

    public void avancarHorasTestePara(FaseHorasTeste novaFase) {
        faseHorasTeste = novaFase;
    }

    /**
     * Volta para a votação de pontos e fecha a trilha de horas de teste (o moderador libera de novo se quiser),
     * como no Planning original. Os votos da rodada anterior ficam guardados como histórico.
     */
    public void novaRodada() {
        rodadaAtual++;
        fase = FaseVotacao.PONTOS;
        faseHorasTeste = null;
        horasLiberadasEm = null;
    }

    public void finalizar(Integer pontosFinais, Integer horasFinais, Integer horasTesteFinais) {
        this.pontosFinais = pontosFinais;
        this.horasFinais = horasFinais;
        this.horasTesteFinais = horasTesteFinais;
        status = StatusTarefa.ESTIMADA;
        fase = null;
        encerradaEm = Instant.now();
    }

    /** Encerra sem estimativa, como o "pular" do Planning original. */
    public void pular() {
        status = StatusTarefa.PULADA;
        encerradaEm = Instant.now();
        fase = null;
        faseHorasTeste = null;
        pontosFinais = null;
        horasFinais = null;
        horasTesteFinais = null;
    }

    public boolean emVotacao() {
        return status == StatusTarefa.LIBERADA;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public String getSprint() {
        return sprint;
    }

    public Instant getLiberadaEm() {
        return liberadaEm;
    }

    public Instant getEncerradaEm() {
        return encerradaEm;
    }

    public Instant getHorasLiberadasEm() {
        return horasLiberadasEm;
    }

    public String getDadosExtras() {
        return dadosExtras;
    }

    public void marcarComoDoRedmine(String jsonDoChamado) {
        doRedmine = true;
        jsonRedmine = jsonDoChamado;
    }

    /** O chamado mudou no Redmine depois da importação. A estimativa não muda. */
    public void atualizarDoRedmine(String titulo, String descricao, String dadosExtras, String jsonDoChamado) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.dadosExtras = dadosExtras;
        this.jsonRedmine = jsonDoChamado;
    }

    public boolean isDoRedmine() {
        return doRedmine;
    }

    public String getJsonRedmine() {
        return jsonRedmine;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public StatusTarefa getStatus() {
        return status;
    }

    public FaseVotacao getFase() {
        return fase;
    }

    public FaseHorasTeste getFaseHorasTeste() {
        return faseHorasTeste;
    }

    public int getRodadaAtual() {
        return rodadaAtual;
    }

    public Integer getPontosFinais() {
        return pontosFinais;
    }

    public Integer getHorasFinais() {
        return horasFinais;
    }

    public Integer getHorasTesteFinais() {
        return horasTesteFinais;
    }

    public Sala getSala() {
        return sala;
    }

    void setSala(Sala sala) {
        this.sala = sala;
    }
}
