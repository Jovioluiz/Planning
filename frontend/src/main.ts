import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app';
import { provideRouter } from '@angular/router';
import { routes } from './app/app.routes';
import { importProvidersFrom } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './app/interceptors/auth.interceptor';
import { FormsModule } from '@angular/forms';
import { LocationStrategy, HashLocationStrategy } from '@angular/common';
import 'zone.js'; 
import './polyfills'; // Importando polyfills necessários

// As rotas usam "#" (HashLocationStrategy, abaixo). Um link sem ele, como os e-mails de redefinição de senha enviados
// antes da correção (https://chutometro.dev.br/redefinir-senha?token=...), é convertido antes de o roteador ler a URL.
if (typeof window !== 'undefined' && window.location.pathname.replace(/\/+$/, '') === '/redefinir-senha') {
  window.history.replaceState(null, '', '/#/redefinir-senha' + window.location.search);
}

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    importProvidersFrom(FormsModule),
    // O interceptor traduz o { mensagem } dos erros do backend para o { message } que as telas leem
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LocationStrategy, useClass: HashLocationStrategy }  // 👈 Aqui está o uso do hash
  ]
}).catch(err => console.error(err));
