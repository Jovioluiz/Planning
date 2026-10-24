import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ResumoEstatisticas {
  tarefasEstimadas: number;
  tarefasPuladas: number;
  totalVotos: number;
  participantes: number;
  totalPontos: number | null;
  mediaPontos: number | null;
  totalHoras: number | null;
  mediaHoras: number | null;
  mediaRodadas: number | null;
  taxaConsenso: number | null;
  duracaoMediaMinutos: number | null;
}

export interface EstatisticaSprint {
  sprint: string;
  tarefas: number;
  puladas: number;
  totalPontos: number | null;
  mediaPontos: number | null;
  totalHoras: number | null;
  mediaHoras: number | null;
  mediaRodadas: number | null;
  taxaConsenso: number | null;
}

export interface EstatisticaUsuario {
  usuario: string;
  perfil: string;
  tarefas: number;
  votosPontos: number;
  cafes: number;
  mediaPontos: number | null;
  votosHoras: number;
  mediaHoras: number | null;
  votosHorasTeste: number;
  mediaHorasTeste: number | null;
  /** Diferença média (com sinal) em relação à média do time. Positivo = estima acima. */
  desvioMedioPontos: number | null;
  /** % de votos iguais à carta mais votada da tarefa. */
  taxaAcertoModa: number | null;
}

export interface EstatisticaTarefa {
  id: number;
  numero: number;
  titulo: string;
  sprint: string;
  sala: string | null;
  votantes: number;
  modaPontos: number | null;
  mediaPontos: number | null;
  minPontos: number | null;
  maxPontos: number | null;
  mediaHoras: number | null;
  mediaHorasTeste: number | null;
  /** Definidos pelo moderador ao finalizar; null em tarefas antigas. */
  pontosFinais: number | null;
  horasFinais: number | null;
  horasTesteFinais: number | null;
  rodadas: number;
  consenso: boolean;
  estimadaEm: string | null;
  duracaoMinutos: number | null;
}

export interface Estatisticas {
  resumo: ResumoEstatisticas;
  porSprint: EstatisticaSprint[];
  porUsuario: EstatisticaUsuario[];
  /** Chave = carta (0 = café). */
  distribuicaoPontos: Record<string, number>;
  distribuicaoHoras: Record<string, number>;
  tarefas: EstatisticaTarefa[];
  salas: { id: number; nome: string }[];
  sprints: string[];
}

export interface FiltroEstatisticas {
  salaId?: number | null;
  sprint?: string | null;
  de?: string | null;
  ate?: string | null;
}

@Injectable({ providedIn: 'root' })
export class EstatisticasService {
  private api = `${environment.apiUrl}/api/estatisticas`;

  constructor(private http: HttpClient) {}

  private get authHeaders() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { Authorization: `Bearer ${token}` } : undefined;
  }

  buscar(filtro: FiltroEstatisticas): Observable<Estatisticas> {
    let params = new HttpParams();
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== null && valor !== undefined && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    return this.http.get<Estatisticas>(this.api, { params, headers: this.authHeaders });
  }
}
