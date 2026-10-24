import {Buffer} from 'buffer';

// Polyfills para ambiente Angular+Vite

// Corrige erro "global is not defined"
(window as any).global = window;

// Corrige erro "process is not defined"
(window as any).process = {
  env: { DEBUG: undefined },
};

// Corrige erro "Buffer is not defined"
(window as any).Buffer = Buffer;
