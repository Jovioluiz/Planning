// Build de produção para a hospedagem na própria máquina (hospedagem-local/): ng build --configuration chutometro.
// O navegador chega ao WildFly pelo Cloudflare Tunnel de api.chutometro.dev.br (-> 127.0.0.1:8082).
export const environment = {
  production: true,
  apiUrl: 'https://api.chutometro.dev.br/planning'
};
