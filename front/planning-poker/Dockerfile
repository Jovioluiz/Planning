# Etapa 1: build Angular SSR
FROM node:20-alpine AS build
WORKDIR /app

# URL do backend — sobrescreva via --build-arg ou docker-compose build args
ARG API_URL=http://localhost:8081

COPY package*.json ./
RUN npm ci --quiet
COPY . .

# Injeta a URL correta antes do build de produção
RUN echo "export const environment = { production: true, apiUrl: '${API_URL}' };" \
    > src/environments/environment.prod.ts

RUN npm run build

# Etapa 2: imagem de execução
FROM node:20-alpine
WORKDIR /app
COPY --from=build /app/dist ./dist
COPY --from=build /app/node_modules ./node_modules
EXPOSE 4200
CMD ["node", "dist/planning-poker/server/server.mjs"]
