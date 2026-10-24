// Sobrescrito no build do Docker (ARG API_URL). Mesmo serviço do original no Render, agora com o WildFly.
export const environment = {
  production: true,
  apiUrl: 'https://planning-poker-api-y60o.onrender.com/planning'
};
