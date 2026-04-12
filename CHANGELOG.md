# Changelog

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
