# Changelog

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
