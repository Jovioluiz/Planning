import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, defer, from, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap, tap } from 'rxjs/operators';
import { API } from './api-jakarta';

/** GET /api/perfil */
export interface PerfilDTO {
  usuario: string;
  perfil: string;
  email: string | null;
  temFoto: boolean;
  fotoAtualizadaEm: string | null;
}

/** GET /api/ambiente */
export interface AmbienteDTO {
  ambiente: string;
  producao: boolean;
}

/** Lado da foto enviada: o front recorta no centro e reduz antes de mandar (fica em torno de 20 KB). */
const LADO_FOTO = 256;

/**
 * "Meu perfil" (senha, e-mail e foto), o "Esqueci minha senha" e as fotos dos avatares.
 *
 * As fotos vêm de GET /api/fotos/{usuario} com o token (um <img src> não mandaria o header), viram object URLs e
 * ficam em cache pela sessão; trocar a própria foto invalida a entrada e avisa os avatares por {@link fotoMudou$}.
 */
@Injectable({ providedIn: 'root' })
export class PerfilService {
  private http = inject(HttpClient);
  private navegador = isPlatformBrowser(inject(PLATFORM_ID));

  private fotos = new Map<string, Observable<string | null>>();
  private urls = new Map<string, string>();
  private ambiente$?: Observable<AmbienteDTO | null>;

  /** Usuário cuja foto mudou (os avatares dele recarregam). */
  readonly fotoMudou$ = new BehaviorSubject<string | null>(null);

  /**
   * Header com o token, como nos outros services (sala, task, redmine...): o authInterceptor não manda o token em
   * todos os contextos, e sem ele as chamadas chegam anônimas (401).
   */
  private get autorizacao(): Record<string, string> {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  meuPerfil(): Observable<PerfilDTO> {
    return this.http.get<PerfilDTO>(`${API}/perfil`, { headers: this.autorizacao });
  }

  alterarSenha(senhaAtual: string, novaSenha: string): Observable<void> {
    // Sem espaços nas pontas, como o login manda (AuthService.login)
    return this.http.put<void>(`${API}/perfil/senha`, { senhaAtual: senhaAtual.trim(), novaSenha: novaSenha.trim() },
      { headers: this.autorizacao });
  }

  alterarEmail(email: string | null): Observable<PerfilDTO> {
    return this.http.put<PerfilDTO>(`${API}/perfil/email`, { email: email?.trim() || null },
      { headers: this.autorizacao });
  }

  /** Recorta no centro, reduz para 256x256 e envia em JPEG. */
  enviarFoto(arquivo: File, usuario: string): Observable<PerfilDTO> {
    return defer(() => from(reduzirImagem(arquivo, LADO_FOTO))).pipe(
      switchMap(blob => this.http.put<PerfilDTO>(`${API}/perfil/foto`, blob, {
        headers: { ...this.autorizacao, 'Content-Type': 'image/jpeg' }
      })),
      tap(() => this.invalidarFoto(usuario))
    );
  }

  removerFoto(usuario: string): Observable<PerfilDTO> {
    return this.http.delete<PerfilDTO>(`${API}/perfil/foto`, { headers: this.autorizacao }).pipe(tap(() => this.invalidarFoto(usuario)));
  }

  /** Object URL da foto do usuário, ou null se ele não tem foto (ou deu erro). */
  foto(usuario: string): Observable<string | null> {
    if (!this.navegador || !usuario) return of(null);
    const chave = usuario.toUpperCase();
    let foto$ = this.fotos.get(chave);
    if (!foto$) {
      foto$ = this.http.get(`${API}/fotos/${encodeURIComponent(usuario)}`, {
        observe: 'response', responseType: 'blob', headers: this.autorizacao
      })
        .pipe(
          map(resposta => {
            if (resposta.status !== 200 || !resposta.body || resposta.body.size === 0) return null;
            const url = URL.createObjectURL(resposta.body);
            this.urls.set(chave, url);
            return url;
          }),
          catchError(() => of(null)),
          shareReplay(1)
        );
      this.fotos.set(chave, foto$);
    }
    return foto$;
  }

  invalidarFoto(usuario: string): void {
    const chave = usuario.toUpperCase();
    const url = this.urls.get(chave);
    if (url) URL.revokeObjectURL(url);
    this.urls.delete(chave);
    this.fotos.delete(chave);
    this.fotoMudou$.next(chave);
  }

  /** Esqueci minha senha (público): o backend responde igual exista ou não o usuário. */
  esqueciSenha(usuarioOuEmail: string): Observable<void> {
    return this.http.post<void>(`${API}/auth/esqueci-senha`, { usuarioOuEmail: usuarioOuEmail.trim() });
  }

  /** Nova senha pelo link do e-mail; devolve o usuário (para preencher o login). */
  redefinirSenha(token: string, novaSenha: string): Observable<{ usuario: string; perfil: string }> {
    return this.http.post<{ usuario: string; perfil: string }>(`${API}/auth/redefinir-senha`,
      { token, novaSenha: novaSenha.trim() });
  }

  /** Ambiente do servidor (uma vez por carga da página). */
  ambiente(): Observable<AmbienteDTO | null> {
    if (!this.navegador) return of(null);
    this.ambiente$ ??= this.http.get<AmbienteDTO>(`${API}/ambiente`).pipe(
      catchError(() => of(null)),
      shareReplay(1)
    );
    return this.ambiente$;
  }
}

/** Recorta o quadrado central da imagem e o reduz para lado x lado, em JPEG. */
async function reduzirImagem(arquivo: File, lado: number): Promise<Blob> {
  if (!arquivo.type.startsWith('image/')) throw new Error('Escolha um arquivo de imagem.');
  const bitmap = await createImageBitmap(arquivo).catch(() => {
    throw new Error('Não foi possível ler esta imagem. Tente uma foto JPEG ou PNG.');
  });
  const menor = Math.min(bitmap.width, bitmap.height);
  const canvas = document.createElement('canvas');
  canvas.width = lado;
  canvas.height = lado;
  const contexto = canvas.getContext('2d')!;
  contexto.fillStyle = '#ffffff'; // PNG com transparência vira fundo branco no JPEG
  contexto.fillRect(0, 0, lado, lado);
  contexto.imageSmoothingQuality = 'high';
  contexto.drawImage(bitmap, (bitmap.width - menor) / 2, (bitmap.height - menor) / 2, menor, menor, 0, 0, lado, lado);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Não foi possível converter a imagem.')),
      'image/jpeg', 0.88));
}
