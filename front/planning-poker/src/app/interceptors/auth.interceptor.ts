import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Se o token existir e a requisição for para a nossa API (evita enviar token para APIs externas), injeta o header
  if (token && req.url.includes('/api/')) {
    const clonedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(clonedReq);
  }

  // Se não tem token (ex: rota de login), passa a requisição original adiante
  return next(req);
};