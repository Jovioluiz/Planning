import { HttpInterceptorFn } from '@angular/common/http';

// Lê o token diretamente do sessionStorage em vez de usar inject(AuthService),
// pois inject() pode falhar quando o HttpClient é chamado fora do contexto de
// injeção do Angular (ex: callbacks de PapaParse, setTimeout, etc.).
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = typeof sessionStorage !== 'undefined'
    ? sessionStorage.getItem('token')
    : null;

  if (token && req.url.includes('/api/')) {
    return next(req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    }));
  }

  return next(req);
};
