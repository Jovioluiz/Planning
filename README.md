# Chutometro — Planning Poker

Aplicação web de Planning Poker para estimativas ágeis em equipe, com votação em tempo real via WebSocket, salas isoladas por código, votação em múltiplas fases (pontos, horas e horas de teste) e painel de moderação completo.

> **Demo:** [chutometro.dev.br](https://chutometro.dev.br)

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17 · Spring Boot 3.2.5 · Spring Security · WebSocket (STOMP/SockJS) |
| Frontend | Angular 20 · Standalone Components · SSR (Client Render Mode) · Zoneless |
| Banco de dados | PostgreSQL 16 |
| Autenticação | JWT stateless |
| Infraestrutura | Docker · Docker Compose · Render.com |

---

## Funcionalidades

- **Salas com código único** — Admin cria uma sala e compartilha um link (`/sala/{codigo}`); qualquer perfil pode entrar direto por esse link, com login/cadastro embutido na própria página da sala
- **Votação em múltiplas fases** — pontos Fibonacci (1, 2, 3, 5, 8, 13, 21, ☕), horas (1–24) e uma fase opcional de **horas de teste** para o perfil `TESTE`
- **Tempo real** — WebSocket notifica todos os clientes da sala sobre revelação de votos, nova rodada, entrada/saída de participantes, tarefas finalizadas/puladas
- **Carta café** — participante pode indicar que não tem estimativa sem bloquear a rodada
- **Moderação por sala** — apenas o moderador (criador) da sala libera tarefas, revela votos, inicia nova rodada, finaliza/pula tarefas e remove participantes
- **Reativação automática de salas** — uma sala marcada como inativa volta a ficar ativa automaticamente se ainda houver tarefas não estimadas nela
- **Estatísticas automáticas** — média, mediana, mínimo e máximo calculados após a revelação
- **Destaque de divergência** — classificação visual de consenso (total / próximo / alta divergência)
- **Importação de tarefas** — upload de CSV (colunas: `numero`, `titulo`, `descricao`, `sprint`) via PapaParse, tanto para o backlog geral quanto para uma sala específica
- **Gerenciamento de usuários** — Super Usuário pode listar e excluir contas
- **Perfis de acesso** — JOGADOR, OBSERVADOR, ADMIN, SUPER, TESTE, com permissões distintas

---

## Perfis de Usuário

| Perfil | Descrição |
|---|---|
| `JOGADOR` | Participa das votações de pontos e horas |
| `OBSERVADOR` | Acompanha as votações sem votar (vê os votos ainda ocultos) |
| `TESTE` | Perfil de QA — acompanha as votações e vota na fase opcional de horas de teste |
| `ADMIN` | Cria e modera salas, importa e gerencia tarefas |
| `SUPER` | Gerencia usuários da plataforma |

> O primeiro login com o perfil ADMIN cria a conta automaticamente (sem ADMIN existente no banco). O mesmo vale para SUPER — os demais perfis (JOGADOR, OBSERVADOR, TESTE) são criados livremente no primeiro login.

---

## Sistema de Salas

- Um `ADMIN` cria uma sala (`POST /api/salas`), que recebe um **código único** (UUID) e vira o **moderador** dessa sala
- O moderador compartilha o link `https://.../#/sala/{codigo}` — qualquer usuário que acessa o link pode fazer login/cadastro na própria página e entra automaticamente na sala
- Ao entrar, `JOGADOR`es são vinculados automaticamente às tarefas ainda não estimadas da sala (fila e liberadas)
- Tarefas podem ser importadas diretamente para uma sala (`POST /api/salas/{salaId}/tasks/importar`) e toda a moderação (liberar, revelar, nova rodada, finalizar, pular, remover participante) é escopada à sala — apenas o moderador daquela sala pode executá-la
- Eventos de WebSocket de uma sala são publicados em `/topic/sala/{salaId}/estimativas`, isolando notificações entre salas diferentes
- Uma sala inativa (`ativa = false`) é **reativada automaticamente** quando o moderador acessa `/api/salas/minhas`, desde que ainda existam tarefas não estimadas nela

---

## Estrutura do Projeto

```
Planning/
├── back/                   # Spring Boot (Java 17, Maven)
├── front/planning-poker/   # Angular 20
├── bin/                    # Artefatos compilados
└── docker-compose.yml      # Orquestração local
```

---

## Rodando Localmente

### Pré-requisitos

- Docker e Docker Compose
- (Desenvolvimento manual) Java 17+, Maven, Node.js 20+, Angular CLI

### Com Docker Compose

```bash
cd Planning
docker compose up --build
```

| Serviço | URL |
|---|---|
| Frontend | http://localhost:4200 |
| Backend | http://localhost:8081 |
| PostgreSQL | localhost:5433 |

### Sem Docker (desenvolvimento)

**Backend:**
```bash
cd back
mvn spring-boot:run
```

**Frontend:**
```bash
cd front/planning-poker
ng serve
```

> O banco precisa estar rodando na porta `5433` com banco `planningdb`, usuário `postgres`, senha `postgres`.

---

## Variáveis de Ambiente (Backend)

| Variável | Padrão (dev) | Descrição |
|---|---|---|
| `PORT` | `8081` | Porta HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5433/planningdb` | URL do banco |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASS` | `postgres` | Senha do banco |
| `JWT_SECRET` | *(insecure dev key)* | Segredo para assinatura JWT — **mude em produção** |
| `ALLOWED_ORIGINS` | `http://localhost:4200` | Origens permitidas no CORS |

---

## Fluxo de Votação

```
Admin cria a sala e compartilha o link /sala/{codigo}
    └─► Participantes entram na sala (login/cadastro embutido)
            └─► Tarefa na fila
                    └─► Moderador libera tarefa
                            └─► Jogadores votam em PONTOS (Fibonacci / ☕)
                                    └─► Moderador revela pontos
                                            └─► Moderador libera votação de horas
                                                    └─► Jogadores votam em HORAS
                                                            └─► Moderador revela horas
                                                                    ├─► (opcional) Moderador libera horas de TESTE
                                                                    │       └─► Perfil TESTE vota e moderador revela
                                                                    └─► Moderador finaliza tarefa ✔
```

Em qualquer etapa o moderador pode iniciar nova rodada (limpa os votos da rodada) ou pular a tarefa (devolve para a fila).

---

## WebSocket — Tópicos

| Tópico | Uso | Eventos |
|---|---|---|
| `/topic/sessoes` | Global | `USUARIO_CONECTADO`, `USUARIO_DESCONECTADO` |
| `/topic/sala/{salaId}/estimativas` | Por sala | `REVELAR_PONTOS`, `REVELAR_HORAS`, `HORAS_LIBERADAS`, `HORAS_TESTE_LIBERADAS`, `REVELAR_HORAS_TESTE`, `NOVA_RODADA`, `VOTO_REGISTRADO`, `VOTO_TESTE_REGISTRADO`, `PARTICIPANTE_REMOVIDO`, `TAREFA_FINALIZADA`, `TAREFA_PULADA` |
| `/topic/estimativas` | Legado (tarefas sem sala) | mesmos eventos acima |

---

## Endpoints REST

### Autenticação e usuários

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | público | Login/registro |
| GET | `/api/auth/jogadores` | público | Lista usernames de JOGADOR |
| POST | `/api/auth/entrar-sala` | autenticado | Entra em uma sala pelo código |
| POST | `/api/auth/selecionar-sprint` | autenticado | Vincula usuário a sprint |
| GET | `/api/auth/usuarios` | SUPER | Lista usuários (exceto SUPER) |
| DELETE | `/api/auth/usuarios/{usuario}` | SUPER | Exclui usuário |
| GET | `/api/sessoes/online` | autenticado | Lista usuários online |

### Salas

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/api/salas` | ADMIN | Cria uma sala |
| POST | `/api/salas/{codigo}/entrar` | autenticado | Entra em uma sala pelo código |
| GET | `/api/salas/minhas` | ADMIN | Lista salas do moderador (reativa as que têm tarefas pendentes) |
| GET | `/api/salas/codigo/{codigo}` | público | Dados básicos de uma sala pelo código |
| GET | `/api/salas/{salaId}/tasks` | autenticado | Lista tarefas da sala |
| GET | `/api/salas/{salaId}/tasks/fila` | autenticado | Tarefas na fila da sala |
| GET | `/api/salas/{salaId}/tasks/liberadas` | autenticado | Tarefas em votação na sala |
| GET | `/api/salas/{salaId}/tasks/votadas` | autenticado | Tarefas estimadas na sala |
| GET | `/api/salas/{salaId}/tasks/{id}/participantes` | autenticado | Participantes da tarefa |
| POST | `/api/salas/{salaId}/tasks/importar` | moderador da sala | Importa tarefas para a sala |
| POST | `/api/salas/{salaId}/tasks/{id}/liberar` | moderador da sala | Libera tarefa para votação |
| POST | `/api/salas/{salaId}/tasks/{id}/liberar-horas` | moderador da sala | Libera votação de horas |
| POST | `/api/salas/{salaId}/tasks/{id}/liberar-horas-teste` | moderador da sala | Libera votação de horas de teste |
| POST | `/api/salas/{salaId}/tasks/{id}/finalizar` | moderador da sala | Finaliza tarefa |
| POST | `/api/salas/{salaId}/tasks/{id}/pular` | moderador da sala | Devolve tarefa para a fila |
| DELETE | `/api/salas/{salaId}/tasks/{id}/participantes/{participante}` | moderador da sala | Remove participante |
| DELETE | `/api/salas/{salaId}/tasks/{id}` | moderador da sala | Exclui tarefa da sala |

### Tarefas (backlog geral / legado, sem sala)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/api/tasks` | autenticado | Lista todas as tarefas |
| GET | `/api/tasks/{id}` | autenticado | Busca tarefa por ID |
| POST | `/api/tasks/importar` | ADMIN | Importa tarefas (JSON/CSV) |
| GET | `/api/tasks/liberadas` | autenticado | Tarefas em votação |
| GET | `/api/tasks/votadas` | autenticado | Tarefas estimadas |
| GET | `/api/tasks/fila` | autenticado | Tarefas na fila |
| GET | `/api/tasks/sprints` | autenticado | Lista sprints distintas |
| GET | `/api/tasks/{id}/participantes` | autenticado | Participantes da tarefa |
| POST | `/api/tasks/{id}/liberar` | ADMIN | Libera tarefa para votação |
| POST | `/api/tasks/{id}/liberar-horas` | ADMIN | Libera votação de horas |
| POST | `/api/tasks/{id}/liberar-horas-teste` | ADMIN | Libera votação de horas de teste |
| POST | `/api/tasks/{id}/finalizar` | ADMIN | Finaliza tarefa |
| POST | `/api/tasks/{id}/pular` | ADMIN | Devolve tarefa para fila |
| DELETE | `/api/tasks/{id}/participantes/{participante}` | ADMIN | Remove participante |
| DELETE | `/api/tasks/excluirTarefa/{id}` | ADMIN | Exclui tarefa |

### Estimativas

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/api/tarefas/{taskId}/estimativas/listar` | autenticado | Lista estimativas |
| GET | `/api/tarefas/{taskId}/estimativas/listar-teste` | autenticado | Lista estimativas de horas de teste |
| GET | `/api/tarefas/{taskId}/estimativas/resumo-votos` | autenticado | Resumo de votos |
| POST | `/api/tarefas/{taskId}/estimativas/votar` | autenticado | Vota em pontos |
| POST | `/api/tarefas/{taskId}/estimativas/votarHoras` | autenticado | Vota em horas |
| POST | `/api/tarefas/{taskId}/estimativas/votarHorasTeste` | TESTE | Vota em horas de teste |
| POST | `/api/tarefas/{taskId}/estimativas/revelarPontos` | autenticado | Revela pontos |
| POST | `/api/tarefas/{taskId}/estimativas/revelar-horas` | autenticado | Revela horas |
| POST | `/api/tarefas/{taskId}/estimativas/revelar-horas-teste` | autenticado | Revela horas de teste |
| POST | `/api/tarefas/{taskId}/estimativas/nova-rodada` | autenticado | Inicia nova rodada |
| POST | `/api/tarefas/{taskId}/estimativas/resetar` | autenticado | Reseta votação |
| GET | `/api/tarefas/{taskId}/estimativas/todos-votaram-pontos` | autenticado | Verifica votos de pontos |
| GET | `/api/tarefas/{taskId}/estimativas/todos-votaram-horas` | autenticado | Verifica votos de horas |
| GET | `/api/tarefas/{taskId}/estimativas/todos-testadores-votaram` | autenticado | Verifica votos de horas de teste |
| DELETE | `/api/tarefas/{taskId}/estimativas/excluirTarefa/{id}` | autenticado | Exclui estimativa |

> **Nota**: `UserController` em `/api/usuarios` duplica os endpoints de SUPER do `AuthController`. Prefira usar os de `/api/auth/usuarios`.

---

## Detalhes da Lógica de Votação

- A "carta café" (pular voto) é armazenada como `pontos = 0` no banco; o frontend usa o sentinel `CARTA_CAFE = -1` localmente
- `todosVotaramPontos`: todos os participantes têm `pontos != null` (inclui 0/café)
- `todosVotaramHoras`: todos os participantes têm `horas != null && horas > 0`
- A votação de **horas de teste** é uma trilha opcional e independente, liberada/revelada separadamente pelo moderador e restrita ao perfil `TESTE`
- Tentativas de voto duplicado retornam HTTP 409
- Horas só podem ser votadas após os pontos (o registro de `Estimation` precisa existir)
- Participantes removidos da tarefa são bloqueados de votar (retorna 403)
- Um jogador que entra durante a fase de horas recebe automaticamente `pontos = 0` (café) para não travar a rodada

---

## Deploy (Render.com)

- **Backend:** `back/render.yaml` — Web Service com variáveis de ambiente configuradas
- **Frontend:** `front/planning-poker/render.yaml` — Static Site
- URL da API de produção definida em `environments/environment.prod.ts`

---

## Desenvolvido por

**Jóvio Luiz Giacomolli** — [github.com/Jovioluiz](https://github.com/Jovioluiz)
