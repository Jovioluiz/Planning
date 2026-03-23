import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http'; // Importar withInterceptors
import { authInterceptor } from './interceptors/auth.interceptor'; // Importar o interceptor que criamos

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes), 
    provideClientHydration(withEventReplay()),
    // Adicionando o interceptor na configuração do HttpClient
    provideHttpClient(
      withInterceptors([authInterceptor])
    )
  ]
};