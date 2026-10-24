import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { mensagemDeErro } from '../services/api-jakarta';

// Lê o token diretamente do sessionStorage em vez de usar inject(AuthService),
// pois inject() pode falhar quando o HttpClient é chamado fora do contexto de
// injeção do Angular (ex: callbacks de PapaParse, setTimeout, etc.).
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = typeof sessionStorage !== 'undefined'
    ? sessionStorage.getItem('token')
    : null;

  const autenticada = token && req.url.includes('/api/')
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  // O backend novo responde { mensagem, detalhes }; os componentes leem err.error.message (formato do antigo)
  return next(autenticada).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' && !err.error.message) {
        const message = mensagemDeErro(err.error);
        if (message) {
          return throwError(() => new HttpErrorResponse({
            error: { ...err.error, message },
            headers: err.headers,
            status: err.status,
            statusText: err.statusText,
            url: err.url ?? undefined
          }));
        }
      }
      return throwError(() => err);
    })
  );
};
