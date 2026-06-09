# Changelog

## 2026-04-28 — Super Usuário, gerenciamento de usuários, temporização de votos e correções de SSR

### Backend

#### `TipoPerfil` — novo perfil SUPER
- **`back/.../entity/enums/TipoPerfil.java`**
  - Valor `SUPER` adicionado ao enum; armazenado como VARCHAR sem necessidade de migração de banco

#### `AuthController` — gerenciamento de usuários e regras de criação
- **`back/.../auth/AuthController.java`**
  - Auto-criação do primeiro ADMIN via login permitida quando nenhum ADMIN existe; a partir do segundo, exige conta existente
  - Auto-criação do SUPER via login permitida quando nenhum SUPER existe; a partir do segundo, bloqueada com 403
  - `GET /api/auth/usuarios` — lista todos os usuários (exceto SUPER); requer role `ROLE_SUPER`
  - `DELETE /api/auth/usuarios/{usuario}` — exclui usuário (não permite excluir o próprio SUPER); requer role `ROLE_SUPER`
  - Helper privado `isSuper(Authentication)` para verificação de role

#### `UserController` — controller de usuários alternativo
- **`back/.../controller/UserController.java`** *(novo)*
  - `GET /api/usuarios` e `DELETE /api/usuarios/{usuario}` espelhando os endpoints do `AuthController`
  - Usa `SecurityContextHolder` diretamente (alternativa ao parâmetro `Authentication`)

#### `DataSeeder` — desativação do seed automático
- **`back/.../config/DataSeeder.java`**
  - Chamadas de criação automática dos usuários `admin`, `jogador1` e `obs1` comentadas; banco inicia vazio

#### `Estimation` e `Task` — campos de temporização
- **`back/.../entity/Estimation.java`**
  - Campos `votadoEmPontos` (Instant) e `votadoEmHoras` (Instant) adicionados; preenchidos ao registrar voto
- **`back/.../entity/Task.java`**
  - Campos `horasLiberadasEm` (Instant) e `estimadaEm` (Instant) adicionados; preenchidos ao liberar horas e ao finalizar tarefa

#### `EstimationController` — persistência de timestamps e restrição de participantes removidos
- **`back/.../controller/EstimationController.java`**
  - `votar` e `votarHoras` registram o timestamp no momento do voto
  - Participantes removidos da tarefa são bloqueados de votar (retorna 403)
  - Helper `calcularSegundos(Instant, Instant)` para duração entre eventos

#### `EstimationNotificationService` — payload estendido
- **`back/.../notification/service/EstimationNotificationService.java`**
  - Notificação de remoção de participante inclui payload adicional com dados do participante removido

#### `EstimativaResponseDTO` — campos de tempo
- **`back/.../dto/EstimativaResponseDTO.java`**
  - Campos de tempo de voto adicionados ao DTO de resposta

---

### Frontend

#### Rodapé global com créditos
- **`front/.../app.html`**
  - Footer com nome do desenvolvedor (Jóvio Luiz Giacomolli) e link para o repositório no GitHub
- **`front/.../styles.scss`**
  - Estilos `.app-footer`, `.footer-dev`, `.footer-sep`, `.footer-link`, `.footer-gh-icon` adicionados globalmente

#### Nova página: Gerenciar Usuários (`/usuarios`)
- **`front/.../pages/gerenciar-usuarios/gerenciar-usuarios.ts`** *(novo)*
  - Componente standalone com `ChangeDetectorRef` (compatível com modo zoneless)
  - `isPlatformBrowser()` impede chamadas HTTP no lado do servidor (SSR-safe)
  - `Authorization` explícito via `HttpHeaders` para contornar possíveis falhas do interceptor em SSR
  - Usuários agrupados por perfil via getter `porPerfil`
  - Listagem via `GET /api/auth/usuarios`; exclusão via `DELETE /api/auth/usuarios/{usuario}`
- **`front/.../pages/gerenciar-usuarios/gerenciar-usuarios.html`** *(novo)*
  - Usuários agrupados em seções (Moderadores, Players, Observadores)
  - Modal de confirmação antes de excluir
- **`front/.../pages/gerenciar-usuarios/gerenciar-usuarios.scss`** *(novo)*
  - Tema escuro alinhado com o restante do app; badge roxo para identificar o Super Usuário

#### Login — suporte ao perfil Super Usuário
- **`front/.../pages/login/login.html`**
  - Opção `Super Usuário` adicionada ao dropdown de perfil
- **`front/.../pages/login/login.ts`**
  - Após login bem-sucedido como SUPER, redireciona para `/usuarios`

#### `AuthService` — método `isSuper()`
- **`front/.../services/auth.service.ts`**
  - `isSuper(): boolean` adicionado (compara perfil salvo no `sessionStorage` com `'SUPER'`)

#### Painel Admin — coluna de horas e temporização de votos
- **`front/.../importar-tarefas/importar-tarefas.ts`**
  - Exibição do tempo por voto calculado a partir dos timestamps do backend
  - Totais e médias de horas por tarefa com cálculo e cache de resumos
  - Coluna "Horas Estimadas" adicionada à tabela de tarefas
- **`front/.../importar-tarefas/importar-tarefas.html`**
  - Coluna de horas estimadas e totalizador exibidos na tabela
- **`front/.../importar-tarefas/importar-tarefas.scss`**
  - Refinamentos visuais de tema claro/escuro e ajustes de layout

#### Tela de Estimativas — tempo por voto
- **`front/.../pages/estimation-board/estimation-board.ts`**
  - Exibe tempo decorrido desde a abertura da votação para cada voto
- **`front/.../pages/estimation-board/estimation-board.html`** e `.scss`
  - Layout e estilos para exibição de temporização

#### Roteamento e SSR
- **`front/.../app.routes.ts`**
  - Rota `/usuarios` com `canActivate: [authGuard]` adicionada
- **`front/.../app.routes.server.ts`**
  - `RenderMode.Client` definido para `/usuarios` (evita requisição sem token no servidor)
- **`front/.../app.config.ts`**
  - `withHttpTransferCacheOptions({ filter: (req) => !req.url.includes('/api/') })` — impede que respostas de endpoints autenticados sejam cacheadas pelo transfer cache do SSR

#### `authGuard` — compatibilidade com SSR
- **`front/.../guards/auth.guard.ts`**
  - `isPlatformBrowser()` adicionado: em contexto servidor retorna `true` sem tentar ler `sessionStorage`

---

## 2026-04-26 — Rodadas de votação, rastreamento de sessões, alternância de tema e redesign do login

### Backend

#### `SessaoService` — rastreamento de usuários online via WebSocket
- **`back/.../service/SessaoService.java`** *(novo)*
  - `ConcurrentHashMap<String, Instant> sessoes` mantido em memória; registra conexões e desconexões via `@EventListener SessionConnectEvent` / `SessionDisconnectEvent`
  - `getUsuariosOnline()` retorna lista de usuários com sessão WebSocket ativa
  - `SessaoController` expõe `GET /api/sessao/online` para consulta da lista de online

#### `EstimationService` — votação filtrada por participantes online
- **`back/.../service/EstimationService.java`**
  - `participantesOnlineDaTarefa(taskId)`: intersecção entre JOGADORs vinculados à tarefa e usuários atualmente online; fallback para todos os JOGADORs quando `sessoes` está vazio (ex.: após restart)
  - `todosVotaramPontos` e `todosVotaramHoras` usam `participantesOnlineDaTarefa` em vez do total de participantes

#### `EstimationController` — suporte a rodadas
- **`back/.../controller/EstimationController.java`**
  - Campo `rodada` adicionado em `Estimation` e nos endpoints de votação/listagem
  - Endpoint `POST /estimativas/{taskId}/nova-rodada` — incrementa rodada e limpa votos anteriores
  - Validação de usuário via JWT em todos os endpoints de voto

#### Entidades e repositórios atualizados
- **`back/.../entity/Estimation.java`** — campo `rodada` (Integer) adicionado
- **`back/.../entity/Task.java`** — campo `rodadaAtual` adicionado
- **`back/.../entity/TaskParticipant.java`** — `@ManyToOne` para `User` (antes era String)
- **`back/.../entity/UserSprint.java`** — vínculo direto com `User`
- **`back/.../repository/EstimationRepository.java`** — queries `findByTaskIdAndRodada`, `findByTaskIdAndUsuarioAndRodada`
- **`back/.../repository/TaskParticipantRepository.java`** — query `findByTaskIdAndUsuario`

---

### Frontend

#### Painel Admin (`importar-tarefas`) — reformulação completa
- **`front/.../importar-tarefas/importar-tarefas.ts`**
  - Alternância de tema claro/escuro: `temaEscuro` + `toggleTema()` com persistência em `localStorage`
  - Painel de usuários online: `usuariosOnline[]` consultado via `GET /api/sessao/online`
  - Suporte a rodadas: `rodadaAtual`, `estimativasRodadaAtual` (filtradas por rodada)
  - `quemNaoVotou` filtra apenas participantes online (sem exigir voto de usuários offline)
  - `verificarLiberacoes` executado localmente no frontend (sem chamada extra ao backend) para garantir sincronia com dados já carregados
  - Callback pattern em `carregarResumoVotos` evita condição de corrida ao checar se todos votaram
  - Botão "Nova Rodada" dispara `POST /estimativas/{id}/nova-rodada`
  - Opção de exportação de resultados
- **`front/.../importar-tarefas/importar-tarefas.html`**
  - `[class.tema-escuro]="temaEscuro"` na raiz; botão `☀️/🌙` no header
  - Painel de usuários online na barra lateral
  - Exibição de rodada atual e histórico de rodadas anteriores
  - Controles de nova rodada e liberação de horas
- **`front/.../importar-tarefas/importar-tarefas.scss`**
  - Bloco `.admin-page.tema-escuro { ... }` com overrides completos (background `#0f172a`, cards `#1e293b`, bordas `#334155`, toasts e banners invertidos)
  - Estilos para painel de usuários online, controles de rodada e exportação

#### Tela de login — redesign visual completo
- **`front/.../pages/login/login.html`** *(reescrita)*
  - Layout two-panel: painel esquerdo de branding (gradiente azul, cartas flutuantes decorativas, logo, tagline, lista de features) + painel direito com formulário
  - Campos com ícone emoji embutido via `.input-wrap` + `.input-icon`
  - Select de perfil com seta CSS (`::after`)
- **`front/.../pages/login/login.scss`** *(reescrita)*
  - `display: flex; min-height: 100vh` — split horizontal
  - Painel esquerdo: `background: linear-gradient(145deg, #1c4f9c 0%, #327ACF 55%, #4a9ade 100%)`
  - Cartas decorativas com `@keyframes float` (oscilação vertical)
  - Painel direito: `width: 460px`, `background: #f8fafc`
  - Cor primária `#327ACF`; foco com `box-shadow: 0 0 0 3px rgba(50,122,207,.15)`
  - Responsivo: empilhamento vertical em `max-width: 768px`; features ocultas no mobile

#### Tela de estimativas — suporte a rodadas
- **`front/.../pages/estimation-board/estimation-board.ts`** e `.html`
  - Exibe rodada atual; adapta UI para aguardar abertura de nova rodada pelo admin
  - WebSocket processa evento de nova rodada e recarrega estado

#### Selecionar Sprint — melhorias
- **`front/.../pages/selecionar-sprint/selecionar-sprint.ts`**
  - Chama `POST /api/auth/selecionar-sprint` ao confirmar sprint; armazena resultado no `AuthService`
- **`front/.../pages/selecionar-sprint/selecionar-sprint.scss`** — estilos adicionados

#### `WebSocketService` — reconexão robusta
- **`front/.../websocket/websocket.service.ts`**
  - Fila de subscriptions pré-conexão agora relida ao reconectar (não apenas na primeira conexão)
  - Heartbeat e reconnect delay ajustados

---

## 2026-04-23 — Ajustes de configuração de CORS e ambiente

### Backend
- **`back/.../config/WebConfig.java`** e **`WebSocketConfig.java`** — atualização de origens permitidas no CORS

### Frontend
- **`front/.../environments/environment.ts`** — atualização da URL da API
- **`front/.../importar-tarefas/importar-tarefas.html`** — ajuste de template

---

## 2026-04-22 — Múltiplas melhorias de UX, estatísticas de votos e gestão de participantes

### Backend

#### Novo endpoint: remover participante
- **`back/.../controller/TaskController.java`**
  - `DELETE /{id}/participantes/{participante}` — remove participante da tarefa ativa e notifica clientes via WebSocket
- **`back/.../service/TaskService.java`**
  - `removerParticipanteDaTarefa(taskId, participante)` implementado
- **`back/.../repository/TaskParticipantRepository.java`** — `deleteByTaskIdAndParticipante` adicionado

#### `Task.liberadaEm`
- **`back/.../entity/Task.java`** — campo `liberadaEm` (Instant) adicionado; definido ao liberar e limpo ao resetar/pular

---

### Frontend

#### Rebrand para "Chutômetro"
- **`front/.../src/index.html`** e **`app.html`** — título e nome da aplicação atualizados para "Chutômetro"

#### Painel Admin — estatísticas e controles avançados
- **`front/.../importar-tarefas/importar-tarefas.ts`**
  - Cronômetro baseado em `task.liberadaEm`: exibe tempo decorrido desde a liberação
  - Estatísticas calculadas após revelação: média, mediana, mínimo, máximo, dispersão e contagem de cafés (para pontos e horas)
  - Classificação de votos por otimista/pessimista
  - Banners de divergência e consenso baseados em dispersão
  - Remoção/pulo de participante: chamada ao novo endpoint `DELETE /{id}/participantes/{participante}`
  - Tarefas estimadas agrupadas por sprint na fila
  - Sprint obrigatória na importação via CSV
- **`front/.../importar-tarefas/importar-tarefas.html`**
  - Cards de estatísticas (média, mediana, min/max, dispersão)
  - Banner de divergência/consenso
  - Botão de pulo/remoção por participante
  - Coluna de sprint na tabela de tarefas
- **`front/.../importar-tarefas/importar-tarefas.scss`**
  - Blocos de estatísticas, cronômetro, banners de divergência, estilos de participantes

#### Tela de espera (`/aguardando`) — nova animação
- **`front/.../pages/aguardando/aguardando.html`** e `.scss`
  - Substituída animação de xícara de café por splash animado
  - Layout e temática atualizados

#### Tela de estimativas — estado `aguardando_horas`
- **`front/.../pages/estimation-board/estimation-board.ts`** e `.html` e `.scss`
  - Estado `aguardando_horas`: jogador vê mensagem de espera até o admin liberar votação de horas
  - Exibição de votos e estatísticas após revelação

---

## 2026-04-20 — Workflow por sprint, infraestrutura Docker e configuração de deploy

### Backend

#### Nova entidade: `UserSprint`
- **`back/.../entity/UserSprint.java`** *(novo)* — vincula usuário à sprint selecionada
- **`back/.../repository/UserSprintRepository.java`** *(novo)*

#### `TaskService` — sprints e liberação de horas
- **`back/.../service/TaskService.java`**
  - `vincularJogadorASprint`: vincula participante apenas à sprint selecionada (não a todas as tarefas)
  - `listarSprints`: retorna sprints distintas com tarefas pendentes
  - `liberarHorasVotacao`: marca `horasLiberadas = true` na tarefa ativa

#### `AuthController` — endpoint `/selecionar-sprint`
- **`back/.../auth/AuthController.java`**
  - Remoção do vínculo automático durante login
  - `POST /api/auth/selecionar-sprint` — persiste sprint escolhida via `UserSprint`

#### `TaskController` — novos endpoints
- **`back/.../controller/TaskController.java`**
  - `GET /tasks/sprints` — lista sprints disponíveis
  - `POST /tasks/{id}/liberar-horas` — libera votação de horas para tarefa ativa

#### Configuração
- **`back/src/main/resources/application.yml`** — ajustes de porta, dialeto Hibernate e variáveis de ambiente
- **`back/Dockerfile`** e **`back/render.yaml`** — imagem Docker e configuração de deploy no Render.com (criados em 2026-04-13, refinados)
- **`docker-compose.yml`** — atualização de portas e variáveis de ambiente

---

### Frontend

#### Nova página: Selecionar Sprint
- **`front/.../pages/selecionar-sprint/selecionar-sprint.ts`** *(novo)*
  - JOGADOR redirecionado após login para escolher sprint antes de entrar no board
  - Sprint selecionada salva no `AuthService` e enviada ao backend
- **`front/.../pages/selecionar-sprint/selecionar-sprint.html`** *(novo)*
- **`front/.../pages/selecionar-sprint/selecionar-sprint.scss`** *(novo)*
- **`front/.../app.routes.ts`** — rota `/selecionar-sprint` adicionada

#### Tela de Importação — campo sprint
- **`front/.../importar-tarefas/importar-tarefas.html`** — coluna sprint adicionada na tabela e campo obrigatório na importação CSV
- **`front/.../importar-tarefas/importar-tarefas.ts`** — `sprint` incluído no payload de importação
- **`front/.../importar-tarefas/importar-tarefas.scss`** — estilos para coluna sprint

#### Tela de Estimativas — liberação de horas pelo admin
- **`front/.../pages/estimation-board/estimation-board.html`** — botão "Liberar Votação de Horas" exibido para ADMIN; estado `aguardando_horas` exibido para jogadores
- **`front/.../pages/estimation-board/estimation-board.ts`** — `liberarHorasVotacao()` implementado; estado `aguardando_horas` adicionado

#### Infraestrutura
- **`front/planning-poker/Dockerfile`** *(novo)* — imagem Docker para o frontend Angular
- **`front/.../environments/environment.ts`** — URL da API atualizada
- **`.gitignore`** *(novo)* — ignora `node_modules`, `target/`, `.env`, logs e artefatos de build

---

## 2026-04-13 — Configuração de deploy e infraestrutura

### Backend
- **`back/Dockerfile`** *(novo)* — imagem Docker multi-stage para Spring Boot (JDK 17 → JRE)
- **`back/render.yaml`** *(novo)* — definição de serviço web no Render.com
- **`back/.../config/WebConfig.java`** — origens de produção adicionadas ao CORS
- **`back/.../config/WebSocketConfig.java`** — origens de produção adicionadas ao CORS WebSocket
- **`back/src/main/resources/application.yml`** — variáveis de ambiente para banco e JWT configuradas para produção

### Frontend
- **`front/planning-poker/angular.json`** — configurações de build ajustadas para produção
- **`front/.../environments/environment.prod.ts`** *(novo)* — URL da API de produção (Render.com)
- **`front/planning-poker/render.yaml`** *(novo)* — definição de site estático no Render.com

### Infra
- **`docker-compose.yml`** *(novo)* — orquestração local: PostgreSQL na porta 5433, backend na 8081, frontend na 4200

---

## 2026-04-12 (commit 2) — Tela de espera animada e detecção de finalização por polling

### Frontend

#### Tela de espera (`/aguardando`) — redesign com animação CSS
- **`front/.../pages/aguardando/aguardando.html`** *(novo arquivo externo)*
  - Template migrado de inline (`template: \`...\``) para arquivo externo
  - Cena do café: xícara com aro, alça, pires, café com reflexo e três wisps de vapor animados
  - Três pontinhos de carregamento pulsantes
  - Subtítulo e botão "Sair"

- **`front/.../pages/aguardando/aguardando.scss`** *(novo)*
  - Xícara em CSS puro com `clip-path: polygon` para forma trapezoidal
  - Vapor: 3 `.wisp` com `filter: blur`, `border-radius` e animação `@keyframes vapor` (sobe, oscila, desvanece) com delays defasados (0s, 0.7s, 1.4s)
  - Conjunto xícara + pires com animação `@keyframes float` (oscilação vertical de 10px a cada 3.2s)
  - Reflexo da superfície do café com `@keyframes glare-pulse`
  - Pontinhos com `@keyframes dot-pulse`
  - Tema dark (`#141b2d`) alinhado com o restante do app

- **`front/.../pages/aguardando/aguardando.ts`**
  - Substituído `template: \`...\`` por `templateUrl` e `styleUrls` apontando para os novos arquivos

#### Tela de estimativas — correção do fluxo pós-finalização
- **`front/.../pages/estimation-board/estimation-board.ts`**
  - `wasLiberated: boolean` adicionado para rastrear se a tarefa já esteve liberada na sessão atual
  - Polling expandido: agora sempre chama `carregarTarefa()` enquanto `sessionEnded` for nulo (antes parava quando `estadoVotacao === 'finalizado'`, perdendo eventos de finalização)
  - `sincronizarEstado` reescrito com detecção de encerramento de sessão por polling:
    - `tarefa.estimada === true` → `sessionEnded = 'finalizada'` (fallback caso o evento WebSocket seja perdido)
    - `tarefa.liberada` transitando de `true` para `false` sem `estimada` → `sessionEnded = 'pulada'`
    - Intervalo cancelado imediatamente ao detectar encerramento
  - Método `irParaAguardando()` adicionado: navega para `/aguardando` sem fazer logout
  - Opções de horas ampliadas: `[1,2,3,4,5,6,7,8,10,12,14,16,20,24]` (antes `[1,2,4,6,8,10,12,16,20,24]`)

- **`front/.../pages/estimation-board/estimation-board.html`**
  - Botão do overlay "Sessão Encerrada" alterado de `logout()` (que deslogava o usuário) para `irParaAguardando()` — o jogador é direcionado à tela de espera animada ao invés de ser forçado ao login

---

## 2026-04-12 — Sistema de participantes por tarefa, fluxo de revelação corrigido, redesign completo das telas

### Backend

#### Nova entidade: `TaskParticipant`
- **`back/.../entity/TaskParticipant.java`** *(novo)*
  - Entidade que vincula participantes esperados a cada tarefa com constraint única `(task_id, participante)`
- **`back/.../repository/TaskParticipantRepository.java`** *(novo)*
  - Métodos: `findByTaskId`, `countByTaskId`, `existsByTaskIdAndParticipante`, `deleteByTaskId` (`@Transactional`)

#### `TaskService` — reescrita com sistema de participantes
- **`back/.../service/TaskService.java`**
  - `importarDTOs`: ao importar tarefas, vincula automaticamente todos os JOGADORs cadastrados
  - `liberarTarefa`: ao liberar tarefa, vincula todos os JOGADORs
  - `vincularJogadorATarefasAtivas`: vincula jogador a todas as tarefas não estimadas (não apenas liberadas)
  - `adicionarParticipante`: idempotente — checa existência antes de inserir
  - `finalizarTarefa`: marca `estimada=true`, `liberada=false`
  - `pularTarefa`: reseta flags, apaga todas as estimativas da tarefa, devolve para a fila
  - `delete`: remove participantes vinculados antes de excluir a tarefa

#### `EstimationService` — `todosVotaram` corrigido
- **`back/.../service/EstimationService.java`**
  - `todosVotaramPontos` e `todosVotaramHoras` agora comparam a contagem de votos contra `participantRepository.countByTaskId` (antes comparava apenas votos existentes entre si, retornando `true` com apenas 1 voto)

#### `EstimationController` — revelação separada por tipo
- **`back/.../controller/EstimationController.java`**
  - Endpoint `/listar` corrigido: pontos exibidos somente quando `isRevealed()==true`; horas somente quando `isHorasReveladas()==true` (antes ambos usavam `isRevealed()`)
  - Coffee card (`pontos==0` no banco) agora retorna `"☕"` em vez do número `0`

#### `EstimativaResponseDTO` — campo `horasReveladas`
- **`back/.../dto/EstimativaResponseDTO.java`**
  - Adicionado campo `horasReveladas` (boolean) e construtor de 5 argumentos

#### `TaskController` — novos endpoints
- **`back/.../controller/TaskController.java`**
  - `GET /{id}/participantes` — lista participantes vinculados à tarefa
  - `POST /{id}/finalizar` — finaliza tarefa; dispara evento WebSocket `TAREFA_FINALIZADA`
  - `POST /{id}/pular` — passa tarefa sem estimar; dispara evento WebSocket `TAREFA_PULADA`

#### `AuthController` — vínculo automático de jogadores
- **`back/.../auth/AuthController.java`**
  - Login de JOGADOR chama `taskService.vincularJogadorATarefasAtivas` para garantir vínculo mesmo quando o admin importou tarefas antes do jogador logar
  - Novo endpoint `GET /api/auth/jogadores` — lista nomes de todos os usuários com perfil `JOGADOR`

#### Outros
- **`back/.../repository/UserRepository.java`** — `findByTipoPerfil(TipoPerfil)` adicionado
- **`back/.../security/JwtAuthenticationFilter.java`** — ajustes relacionados ao novo fluxo de autenticação

---

### Frontend

#### `auth.interceptor.ts` — leitura direta do `sessionStorage`
- **`front/.../interceptors/auth.interceptor.ts`**
  - Interceptor funcional corrigido: ler token diretamente de `sessionStorage` em vez de usar `inject()` fora do contexto de injeção Angular (causava falha silenciosa em callbacks HTTP)

#### `estimation.service.ts` — `authOptions` e métodos de revelação
- **`front/.../services/estimation.service.ts`**
  - Getter `authOptions` adicionado (lê token do `sessionStorage` como fallback)
  - `authOptions` aplicado em todos os métodos: `votar`, `votarHoras`, `listar`, `todosVotaramPontos`, `todosVotaramHoras`, `revelarPontos`, `revelarHoras`, `resetar`, `getResumoVotos`

#### `task.service.ts` — novos métodos
- **`front/.../services/task.service.ts`**
  - `getJogadores()`, `getParticipantesTarefa(taskId)`, `finalizarTarefa(id)`, `pularTarefa(id)`

#### Tela de Estimativas — redesign dark poker + correções de fluxo
- **`front/.../pages/estimation-board/estimation-board.ts`**
  - `ChangeDetectorRef` injetado; `detectChanges()` chamado em todos os callbacks HTTP (necessário no modo zoneless)
  - `sincronizarEstado` corrigido: nunca regride o estado do jogador (jogador que votou pontos não volta para fase 'pontos' ao recarregar tarefa)
  - `atualizarEstimativas` guarda o raw da API diretamente (`estimativas = res`)
  - Polling a cada 5 s enquanto votação ativa; cancelado no `ngOnDestroy`
  - WebSocket: trata `TAREFA_FINALIZADA` e `TAREFA_PULADA` → exibe overlay de sessão encerrada
  - Propriedade `sessionEnded: 'finalizada' | 'pulada' | null`

- **`front/.../pages/estimation-board/estimation-board.html`** *(reescrita completa)*
  - Tema dark de baralho: header sticky, banner da tarefa com badge de fase, grid de participantes, barra admin
  - Jogadores não veem votos dos outros antes do admin revelar (`*ngIf="isAdmin || isObservador"` para o grid em tempo real; jogadores veem apenas contagem de votos com "Aguardando revelar")
  - Após revelação: valores visíveis para todos
  - Overlay de sessão encerrada (`session-ended-overlay`) ao receber `TAREFA_FINALIZADA`/`TAREFA_PULADA`

- **`front/.../pages/estimation-board/estimation-board.scss`** *(reescrita completa)*
  - Tema dark (`#141b2d`); cards de baralho brancos com elevação hover/selected; badge de fase; overlay modal

#### Painel Admin — redesign + controles de votação
- **`front/.../importar-tarefas/importar-tarefas.ts`** *(refatoração significativa)*
  - Polling a cada 5 s em `atualizarStatusVotacao()`: mantém `pontosRevelados`/`horasReveladas` atualizados
  - `participantesTarefa`: lista de participantes vinculados à tarefa ativa
  - Getter `quemNaoVotou`: filtra `participantesTarefa` contra quem já votou
  - Getter `mediaHoras`: média das horas votadas (arredondada a 1 decimal), exibida após `horasReveladas=true`
  - `finalizarVotacao()` e `pularVotacao()` implementados
  - `revelarPontos()` atualiza `tarefaEmVotacao` após revelar para habilitar corretamente o botão de horas

- **`front/.../importar-tarefas/importar-tarefas.html`** *(redesign completo)*
  - Layout com coluna principal + painel lateral sticky
  - Fila de tarefas como tabela com checkbox de seleção de linha
  - Stats no topo: "Na Fila", "Em Votação", "Estimadas"
  - Painel "Votação Ativa": botões Revelar (desabilitados se ainda há quem não votou), lista "Votaram" com pontos/horas, lista "Aguardando" com indicador amarelo
  - Média de horas em card azul após revelação
  - Botões "✓ Finalizar Tarefa" e "↷ Passar sem Estimar"

- **`front/.../importar-tarefas/importar-tarefas.scss`** *(redesign completo)*
  - `.media-horas` (card azul gradiente), `.final-actions`, `.btn-success`, `.btn-warning`, `.btn-ghost`
  - `.pending-voters`, `.pending-name`, `.dot-pending`

- **`front/.../pages/login/login.ts`** e **`login.html`/`login.scss`** — redesign visual da tela de login

---

## 2026-04-09 — Revisão geral de segurança, qualidade e UX

### Backend

#### Segurança (crítico)
- **`back/src/main/java/com/planningapp/security/JwtTokenProvider.java`**
  - Chave JWT agora lida de `application.yml` via `@Value` + `@PostConstruct` (antes gerada em memória a cada restart, invalidando tokens existentes)
  - Adicionado método `getRoleFromJWT()` para extrair o perfil do token
  - `System.err.println` substituído por SLF4J logger

- **`back/src/main/java/com/planningapp/security/JwtAuthenticationFilter.java`**
  - Role do token agora mapeado para `ROLE_ADMIN`, `ROLE_JOGADOR` ou `ROLE_OBSERVADOR` (antes sempre `ROLE_USER`, ignorando o perfil real)

- **`back/src/main/java/com/planningapp/config/WebSocketConfig.java`**
  - Interceptor STOMP corrigido para usar o role real do token JWT na autenticação WebSocket

- **`back/src/main/java/com/planningapp/config/SecurityConfig.java`**
  - Adicionado `@EnableMethodSecurity` para habilitar `@PreAuthorize` nos controllers

#### Autorização por perfil
- **`back/src/main/java/com/planningapp/controller/TaskController.java`**
  - Endpoints `importar`, `liberar` e `excluirTarefa` agora exigem `ROLE_ADMIN`
  - Rota DELETE duplicada (`/{id}`) removida; mantida apenas `/excluirTarefa/{id}` (usada pelo frontend)

- **`back/src/main/java/com/planningapp/controller/EstimationController.java`**
  - `revelarPontos`, `revelarHoras`, `resetar`, `excluirTarefa` exigem `ROLE_ADMIN`
  - `votar` e `votarHoras` exigem `ROLE_ADMIN` ou `ROLE_JOGADOR` (OBSERVADOR bloqueado)
  - Validação adicionada em `votar`: retorna 400 se a tarefa não estiver liberada

#### Tratamento de erros e validação
- **`back/src/main/java/com/planningapp/exception/GlobalExceptionHandler.java`** *(novo)*
  - `@RestControllerAdvice` centralizado; trata `MethodArgumentNotValidException` (400), `AccessDeniedException` (403) e exceções genéricas (500) sem expor stack trace

- **`back/src/main/java/com/planningapp/dto/LoginDTO.java`** — `@NotBlank` em `usuario` e `senha`
- **`back/src/main/java/com/planningapp/dto/TaskDTO.java`** — `@NotNull` em `numero`, `@NotBlank` em `titulo` e `descricao`
- **`back/src/main/java/com/planningapp/dto/EstimativaDTO.java`** — `@NotBlank` em `participante`, `@NotNull` em `pontos`
- **`back/src/main/java/com/planningapp/dto/EstimativaHorasDTO.java`** — `@NotBlank` em `participante`, `@NotNull` e `@Positive` em `horas`
- **`back/src/main/java/com/planningapp/auth/AuthController.java`** — `@Valid` adicionado no endpoint `/login`; checagem manual de null removida (coberta pelo Bean Validation)

#### Transações e logging
- **`back/src/main/java/com/planningapp/service/EstimationService.java`** — `@Transactional` em todas as operações de escrita
- **`back/src/main/java/com/planningapp/service/TaskService.java`** — `@Transactional` em todas as operações de escrita
- **`back/src/main/java/com/planningapp/config/DataSeeder.java`** — `System.out.println` substituído por SLF4J logger

#### Configuração
- **`back/src/main/resources/application.yml`**
  - `show-sql: false` (era `true`, impactava performance e expunha SQL em logs)
  - Credenciais do banco via variáveis de ambiente: `DB_URL`, `DB_USER`, `DB_PASS` (com defaults para desenvolvimento)
  - `jwt.secret` configurável via variável de ambiente `JWT_SECRET`

- **`back/pom.xml`** — dependência `spring-boot-starter-validation` adicionada (necessária para Bean Validation nos DTOs)

---

### Frontend

#### Correções críticas
- **`front/planning-poker/src/app/services/estimation.service.ts`**
  - **Bug corrigido:** `votarHoras` enviava campo `horaSelecionada` mas o backend esperava `horas`
  - Método `revelar()` genérico removido (chamava `/revelar` que não existia no backend)
  - `revelarPontos()` e `revelarHoras()` agora chamados diretamente

- **`front/planning-poker/src/app/services/auth.service.ts`**
  - Adicionados `isJogador()` e `isObservador()`

- **`front/planning-poker/src/app/pages/login/login.ts`**
  - JOGADOR sem tarefa liberada agora navega para `/aguardando` em vez de `/estimativas/0` (rota inválida)

#### Novos arquivos
- **`front/planning-poker/src/app/pages/aguardando/aguardando.ts`** *(novo)*
  - Tela de espera para JOGADOR/OBSERVADOR quando nenhuma tarefa está liberada
  - Faz polling a cada 5 segundos e redireciona automaticamente quando uma tarefa for liberada

- **`front/planning-poker/src/app/app.routes.ts`** — rota `/aguardando` adicionada (com `authGuard`)

#### Votação por fase (admin no EstimationBoard)
- **`front/planning-poker/src/app/pages/estimation-board/estimation-board.ts`**
  - `revelar()` agora chama `revelarPontos` ou `revelarHoras` conforme `estadoVotacao` (pontos → horas → finalizado)
  - `checkTodosVotaram()` verifica a fase atual, não ambas ao mesmo tempo
  - OBSERVADOR detectado via `isObservador()` e bloqueado de votar
  - `votando` (loading state) adicionado para desabilitar botões durante o envio
  - Erros de rede exibidos ao usuário (não apenas `console.error`)

- **`front/planning-poker/src/app/pages/estimation-board/estimation-board.html`**
  - Botão Revelar mostra "Revelar Pontos" ou "Revelar Horas" conforme a fase ativa
  - Cartas desabilitadas durante envio (`[disabled]="votando"`)
  - Aviso visual para OBSERVADOR
  - Mensagem de sucesso com estilo próprio; texto do aviso do admin indica a fase atual

#### UX — ImportarTarefas
- **`front/planning-poker/src/app/importar-tarefas/importar-tarefas.ts`**
  - Todos os `alert()` (5 ocorrências) substituídos por `exibirMensagem()` com auto-dismiss de 5 segundos
  - Tipagem `ITask` aplicada nos arrays (antes `any[]`)
  - Erros de rede exibidos ao usuário
  - Botão "Sair" adicionado

- **`front/planning-poker/src/app/importar-tarefas/importar-tarefas.html`**
  - Div de mensagem inline adicionada (sucesso/erro)
  - Tabelas com `<thead>`/`<tbody>` explícitos
  - Ações da fila de tarefas agrupadas em coluna "Ações"

#### Estilos
- **`front/planning-poker/src/app/importar-tarefas/importar-tarefas.scss`** — classes `.mensagem-sucesso` e `.mensagem-erro` adicionadas; `.top-bar` adicionada
- **`front/planning-poker/src/app/pages/estimation-board/estimation-board.scss`** — classes `.observador-aviso`, `.loading-msg` e `.mensagem-sucesso` adicionadas
