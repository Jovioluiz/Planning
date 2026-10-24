// Backend planning-jakarta. O /planning é o context root do EAR no WildFly (o original não tinha).
// Mesmo domínio do original: o Cloudflare Tunnel de api.chutometro.dev.br passa a apontar para o WildFly (8082).
export const environment = {
  production: false,
  apiUrl: 'https://api.chutometro.dev.br/planning'
};
