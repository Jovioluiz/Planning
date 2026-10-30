/// <reference lib="webworker" />
// Converte Textile fora da thread da tela: um texto que trave o textile-js trava só o worker (ver textile-redmine.ts)
import textile from 'textile-js';

addEventListener('message', ({ data }: MessageEvent<string>) => {
  postMessage(textile.convert(data));
});
