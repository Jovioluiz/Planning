import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';
import inject from '@rollup/plugin-inject';

// Para Angular standalone + Vite
export default defineConfig({
  plugins: [
    angular(),
    inject({
      global: ['globalThis', 'global'], // Corrige "global is not defined"
    }),
  ],
  resolve: {
    alias: {
      // Força o Vite a resolver 'buffer' para o pacote NPM instalado no navegador
      buffer: 'buffer/', 
    }
  },
  define: {
    global: {},
    'process.env': {},
  },
  optimizeDeps: {
    include: ['buffer'],
  },
});