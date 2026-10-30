# Planning Jakarta

Projeto de estudo: uma fatia do Planning Poker (salas e tarefas) reescrita em **Jakarta EE 11**,
empacotada como **EAR** e implantada no **WildFly 41**, com módulos Maven bem separados.

- Java 21
- Jakarta EE 11 (JAX-RS, CDI, EJB, JPA/Hibernate, Bean Validation, JSON-B, Jakarta Security, Jakarta WebSocket)
- Autenticação JWT (HS256) com [nimbus-jose-jwt](https://connect2id.com/products/nimbus-jose-jwt)
- WildFly 41.0.1.Final
- PostgreSQL 16

## Módulos

```
planning-parent/            pom      versões, plugins e lista de módulos
├── planning-model/         jar      entidades JPA e enums
├── planning-dto/           jar      records de request/response (+ Bean Validation)
├── planning-security/      jar      geração/validação de JWT e constantes de papéis
├── planning-persistence/   jar      persistence.xml, repositórios com EntityManager e migrations (Liquibase)
├── planning-service/       ejb      regras de negócio em @Stateless (transação e @RolesAllowed pelo container)
├── planning-web/           war      recursos JAX-RS, WebSocket, mecanismo de autenticação, ExceptionMappers, CORS
├── planning-ear/           ear      junta tudo em planning.ear
└── planning-it/            jar      testes de integração contra um WildFly de verdade (só com -Pintegracao)
```

**Regra de dependência:** `web → service → persistence → model`. O `dto` e o `security` são usados por `service` e `web`.

- O `web` nunca injeta `EntityManager` nem retorna entidade: só conversa com o service e os DTOs.
- As entidades não saem do `service`: o `PlanningMapper` converte tudo para DTO.
- O `persistence` não abre transação: quem define o escopo transacional é o EJB que o chama.

### Estrutura do EAR gerado

```
planning.ear
├── META-INF/application.xml        (context-root /planning)
├── lib/                            visível para todos os módulos
│   ├── planning-model.jar
│   ├── planning-dto.jar
│   ├── planning-security.jar
│   ├── planning-persistence.jar    (contém a persistence unit "planningPU")
│   └── nimbus-jose-jwt.jar         (única dependência externa)
├── planning-service.jar            módulo EJB
└── planning-web.war                sem WEB-INF/lib: as dependências vêm do EAR
```

## Spring Boot × Jakarta EE (comparando com o Planning original)

| Planning (Spring Boot) | Aqui (Jakarta EE) |
|---|---|
| `@RestController` / `@GetMapping` | `@Path` / `@GET` (JAX-RS) |
| `@Service` + `@Transactional` | `@Stateless` (transação JTA automática por método) |
| `@Autowired` | `@Inject` (CDI) |
| `JpaRepository` | classe `@ApplicationScoped` com `@PersistenceContext EntityManager` |
| `@ControllerAdvice` | `ExceptionMapper<T>` + `@Provider` |
| `application.yml` com URL do banco | datasource no WildFly (`java:jboss/datasources/PlanningDS`) |
| `java -jar app.jar` | `planning.ear` em `standalone/deployments/` |
| `JwtAuthenticationFilter` (`OncePerRequestFilter`) | `HttpAuthenticationMechanism` do Jakarta Security |
| `SecurityConfig` com `.hasRole("ADMIN")` / `@PreAuthorize` | `@RolesAllowed` nos EJBs |
| `SecurityContextHolder...getName()` | `SessionContext.getCallerPrincipal().getName()` |
| `BCryptPasswordEncoder` | `Pbkdf2PasswordHash` (do Jakarta Security) |
| jjwt | nimbus-jose-jwt |
| STOMP + SockJS (`/ws-planning`, `/topic/sala/{id}/estimativas`) | `@ServerEndpoint("/ws/salas/{salaId}")` (Jakarta WebSocket puro) |
| `SimpMessagingTemplate.convertAndSend(...)` no service | evento CDI (`Event<EventoSala>.fire`) observado na camada web |

## Autenticação e autorização

1. `POST /api/auth/login` devolve um JWT (claims `sub` = usuário, `groups` = perfil).
2. O cliente envia `Authorization: Bearer <token>` nas demais requisições.
3. O `JwtAuthenticationMechanism` (no `planning-web`) valida o token em toda requisição e chama
   `notifyContainerAboutLogin(usuario, papeis)`. A partir daí o container sabe quem é o usuário.
   O token prova **quem** é; os **papéis** vêm do banco (`AuthService.papeisAtuais`), para que desativar ou mudar
   o perfil de alguém valha na hora, e não só quando o token expirar. Custa uma consulta por índice por requisição.
4. Os EJBs do `planning-service` declaram `@RolesAllowed` e o container barra quem não tem o papel.
   O próprio service verifica se o usuário é o moderador da sala (regra de negócio, não de papel).

| Situação | Resposta |
|---|---|
| Sem token, ou token inválido/expirado, em endpoint protegido | 401 + `WWW-Authenticate: Bearer` |
| Login com usuário ou senha errados | 401 |
| Usuário desativado (login, token antigo ou WebSocket) | 401 / WebSocket fechado com 1008 |
| Perfil sem permissão (ex.: JOGADOR criando sala) | 403 |
| ADMIN que não é o moderador da sala | 403 |

Regras de cadastro (iguais às do Planning original): JOGADOR, OBSERVADOR e TESTE são livres; ADMIN só se ainda
não existir nenhum; SUPER nunca pela API. Outros ADMINs são criados pelo SUPER (ver "Gestão de usuários").

### Usuário SUPER e gestão de usuários

**Criação do SUPER:** no original, só direto no banco. Aqui isso é inviável (a senha fica em hash PBKDF2), então o
EJB `CriacaoSuperUsuario` (`@Singleton @Startup`, o equivalente a um `CommandLineRunner` do Spring) cria o SUPER no
deploy a partir de `SUPER_USUARIO` e `SUPER_SENHA`, se ainda não houver nenhum. Depois disso a senha pode sair do
ambiente: a variável não é mais lida.

**Gestão** (`/api/usuarios`, só SUPER; no original havia só listar e excluir, duplicados em dois paths):

| Operação | Regras |
|---|---|
| Listar | Todos menos os SUPER, com `ativo`, `salasModeradas` e `temHistorico` |
| Criar | Qualquer perfil menos SUPER, **inclusive mais ADMINs** (o cadastro público só aceita o primeiro) |
| Alterar perfil | ADMIN que modera sala ativa não pode deixar de ser ADMIN (409) |
| Redefinir senha | 6 a 100 caracteres |
| Desativar / reativar | Desativado não faz login, o token antigo para de valer e o WebSocket é recusado. ADMIN com sala ativa: 409 |
| Excluir | Só quem **não tem histórico** (votos ou salas). Os demais → 409: desative em vez de excluir, para não apagar votos das estatísticas (o original apagava direto) |

Os SUPER não aparecem na lista e não podem ser alterados pela API (403).

> **Configuração obrigatória no WildFly:** `integrated-jaspi=false` no `application-security-domain` "other" do
> Undertow (já está no `docker/configurar-wildfly.cli`). Sem isso, o WildFly procura o usuário do token no
> próprio realm (`ApplicationDomain`), não encontra e recusa a requisição com `ELY01177: Authorization failed`.

| Variável | Padrão | Descrição |
|---|---|---|
| `JWT_SECRET` | segredo de dev (com aviso no log) | Segredo HMAC, mínimo de 32 caracteres. **Defina em produção.** |
| `JWT_VALIDADE_MINUTOS` | `480` | Validade do token |
| `REDMINE_SEGREDO` | o `JWT_SECRET` | Cifra (AES-256-GCM) a chave da API do Redmine no banco. Trocar depois obriga os moderadores a informar a chave de novo. |
| `SUPER_USUARIO` / `SUPER_SENHA` | — | Criam o SUPER no deploy, se ainda não existir nenhum (senha com 6+ caracteres) |

## WebSocket (tempo real)

Conexão: `ws://localhost:8082/planning/ws/salas/{salaId}?token=<jwt>`. Fica fora do `/api` porque não é JAX-RS.

```
TarefaService (EJB)                 planning-web (qualquer nó)                 planning-web (cada nó)
  eventos.fire(EventoSala) ─commit─► SalaNotificador                          EntregaEventosSala (MDB)
                                     @Observes(AFTER_SUCCESS)                  maxSession=1
                                       └─► PublicadorEventosSala ──JMS──► tópico ──► SalaSessoes.enviar ─► clientes
                                                                 planning.eventos-sala      (só as conexões do nó)
```

- **O service não conhece o WebSocket nem o JMS:** ele só dispara um evento CDI, e a camada web decide como entregar.
- **`AFTER_SUCCESS`:** a mensagem só sai depois do commit. Se a transação falhar, ninguém é avisado. E o contrário:
  se o broker falhar, o voto continua gravado (a notificação vira log e o cliente se recupera relendo a votação).
- **Escala com JMS:** cada nó publica no tópico, e cada nó tem um MDB assinando-o (assinatura não durável) que
  entrega às **suas** conexões. Um voto feito no nó A chega a quem está conectado no nó B.
  - O tópico é criado pela aplicação (`@JMSDestinationDefinition` no MDB). O servidor só precisa de um perfil com
    mensageria: o projeto usa `standalone-full.xml` (Docker, CLI e testes de integração).
  - Publicação com a `java:/ConnectionFactory`, não a `java:/JmsXA`: no `AFTER_SUCCESS` a transação já acabou e a
    JmsXA (JCA, transacional) tentaria se alistar nela.
  - **Ordem:** um só produtor por nó (`synchronized`) e o MDB com `maxSession=1`. Com o paralelismo padrão do MDB,
    `VOTO_REGISTRADO` e `TODOS_VOTARAM` poderiam chegar invertidos (o `TempoRealIT` pega isso).
- **Presença distribuída:** `PresencaSala` (quem está online) fica num cache do Infinispan configurado no servidor
  (`cache-container=planning`, cache `presenca`), então o `PRESENCA` e o "todos votaram" enxergam as conexões de
  todos os nós. Detalhes em "Todos votaram".
- **Token na query string:** a API de WebSocket do navegador não permite headers. O token é validado no
  `@OnOpen`, e uma conexão inválida é fechada com o código **1008**. URLs podem ir parar em logs de proxy;
  se isso importar, use um "ticket" de uso único emitido por um endpoint REST.
- **Checagem de `Origin`** no handshake (`OrigemConfigurator`), contra Cross-Site WebSocket Hijacking.
  Usa a mesma `ALLOWED_ORIGINS` do CORS.
- O cliente pode mandar `ping` e recebe `pong` (keep-alive).

| `tipo` | Quando | Campos |
|---|---|---|
| `PRESENCA` | alguém entrou ou saiu | `usuario`, `online` (lista atualizada) |
| `TAREFA_CRIADA` / `TAREFA_LIBERADA` / `TAREFA_FINALIZADA` / `TAREFA_PULADA` / `TAREFA_EXCLUIDA` | o moderador alterou uma tarefa | `tarefa` |
| `TAREFAS_IMPORTADAS` | o moderador importou tarefas (um aviso por importação) | — (recarregue a lista) |
| `VOTO_REGISTRADO` | um jogador votou (pontos ou horas) | `usuario`, `tarefa` (**nunca o valor do voto**) |
| `PONTOS_REVELADOS` / `HORAS_LIBERADAS` / `HORAS_REVELADAS` / `NOVA_RODADA` | o moderador mudou a fase | `tarefa` (com `fase` e `rodadaAtual`) |
| `VOTO_TESTE_REGISTRADO` | um testador votou nas horas de teste | `usuario`, `tarefa` (**nunca o valor**) |
| `HORAS_TESTE_LIBERADAS` / `HORAS_TESTE_REVELADAS` | o moderador mudou a trilha de teste | `tarefa` (com `faseHorasTeste`) |
| `TODOS_VOTARAM` | todos os JOGADORes online votaram na fase aberta | `tarefa` (com `fase`: `PONTOS` ou `HORAS`) |
| `TODOS_TESTADORES_VOTARAM` | todos os TESTEs online votaram nas horas de teste | `tarefa` |
| `PARTICIPANTE_REMOVIDO` / `PARTICIPANTE_ADICIONADO` | o moderador removeu ou readmitiu alguém | `usuario`, `tarefa` |
| `SALA_DESATIVADA` | o moderador desativou a sala | — |

Todas as mensagens têm `tipo`, `salaId` e `em`. Exemplo:

```json
{"em":"2026-10-19T14:47:58Z","salaId":1,"tipo":"TAREFA_LIBERADA",
 "tarefa":{"id":1,"numero":"PP-1","salaId":1,"status":"LIBERADA","titulo":"Login"}}
```

Cliente no Angular/navegador (sem bibliotecas):

```ts
const ws = new WebSocket(`ws://localhost:8082/planning/ws/salas/${salaId}?token=${token}`);
ws.onmessage = ({ data }) => {
  if (data === 'pong') return;
  const evento = JSON.parse(data);
  if (evento.tipo === 'TAREFA_LIBERADA') { /* abrir votação */ }
};
ws.onclose = ({ code, reason }) => { if (code === 1008) { /* token inválido: voltar ao login */ } };
setInterval(() => ws.readyState === WebSocket.OPEN && ws.send('ping'), 30_000);
```

## Rodar com Docker

```bash
mvn package               # opcional; o Dockerfile também compila
docker compose up --build
```

- API: http://localhost:8082/planning/api
- Console do WildFly: http://localhost:9991 (crie um usuário com `add-user.sh` dentro do container)

As portas são diferentes das do Planning original, então os dois podem rodar ao mesmo tempo.

## Rodar com WildFly local (sem Docker)

1. Baixe o WildFly 41 em https://www.wildfly.org/downloads/ e extraia.
2. Instale o driver do PostgreSQL, crie o datasource e ajuste a segurança para o JWT. Baixe o
   [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar)
   em `/tmp/postgresql.jar` (ou ajuste o caminho no script) e rode:
   ```bash
   $WILDFLY_HOME/bin/jboss-cli.sh --file=docker/configurar-wildfly.cli
   ```
   Defina `DB_URL`, `DB_USER`, `DB_PASS` e `JWT_SECRET` antes de subir o servidor. Sem elas, o banco padrão é `jdbc:postgresql://db:5432/planningjakarta`.
3. Compile e implante:
   ```bash
   mvn package
   cp planning-ear/target/planning.ear $WILDFLY_HOME/standalone/deployments/
   $WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml   # perfil com JMS
   ```
4. Acesse http://localhost:8080/planning/api/salas

### Rodar em cluster (dois ou mais nós)

Testado com dois WildFly na mesma máquina; um voto feito pelo nó A chegou aos clientes conectados no nó B.

- Perfil **`standalone-full-ha.xml`** (mensageria em cluster) em todos os nós, com o mesmo `JWT_SECRET` (o token
  emitido por um nó vale nos outros) e o **mesmo banco**.
- Senha do cluster do Artemis (o padrão é `CHANGE ME!!` e os nós não se conectam sem trocar):
  `/subsystem=messaging-activemq/server=default:write-attribute(name=cluster-password, value=...)`
- Descoberta dos nós pelo JGroups. O padrão é multicast (UDP); onde não há multicast (nuvem, Kubernetes,
  loopback no Windows), troque por uma lista fixa (`TCPPING`) ou pelo `KUBE_PING`/`DNS_PING`:
  ```
  /socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=no-a:add(host=10.0.0.1, port=7600)
  /socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=no-b:add(host=10.0.0.2, port=7600)
  /subsystem=jgroups/stack=tcp/protocol=MPING:remove
  /subsystem=jgroups/stack=tcp/protocol=TCPPING:add(add-index=0, socket-bindings=[no-a, no-b])
  /subsystem=jgroups/channel=ee:write-attribute(name=stack, value=tcp)
  ```
- Cache da presença **replicado** em todos os nós (no lugar do `local-cache` do `configurar-wildfly.cli`):
  ```
  /subsystem=infinispan/cache-container=planning/transport=jgroups:add()
  /subsystem=infinispan/cache-container=planning/replicated-cache=presenca:add()
  ```
- Suba **um nó primeiro**: o Liquibase protege as migrations com lock, mas a criação do SUPER (`CriacaoSuperUsuario`)
  não — dois nós subindo juntos num banco vazio tentariam criá-lo ao mesmo tempo, e um falharia o deploy.
- Um balanceador na frente (com suporte a WebSocket) distribui as conexões; não precisa de sessão "grudada", já que
  a API é stateless (JWT) e os eventos chegam por JMS a qualquer nó.

## Endpoints

| Método | Path | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/registrar` | público | Cadastra `{ "usuario", "senha", "perfil" }` |
| POST | `/api/auth/login` | público | Login `{ "usuario", "senha" }` → `{ token, tipo, expiraEm, usuario }` |
| GET | `/api/auth/me` | logado | Dados do usuário do token |
| GET | `/api/usuarios` | SUPER | Lista os usuários (menos os SUPER) |
| POST | `/api/usuarios` | SUPER | Cria `{ "usuario", "senha", "perfil" }` (inclusive ADMIN) |
| PUT | `/api/usuarios/{usuario}/perfil` | SUPER | `{ "perfil": "OBSERVADOR" }` |
| PUT | `/api/usuarios/{usuario}/senha` | SUPER | `{ "senha": "..." }` |
| POST | `/api/usuarios/{usuario}/desativar` / `reativar` | SUPER | Bloqueia ou libera o acesso (vale na hora) |
| DELETE | `/api/usuarios/{usuario}` | SUPER | Exclui quem não tem histórico |
| GET | `/api/estatisticas?salaId=&sprint=&de=&ate=` | ADMIN / SUPER | Estatísticas das tarefas encerradas (ver seção) |
| GET | `/api/salas` | logado | Lista salas ativas |
| POST | `/api/salas` | ADMIN | Cria sala `{ "nome": "Sprint 42" }`; quem cria vira o moderador |
| GET | `/api/salas/codigo/{codigo}` | público | Busca sala pelo código (UUID) |
| POST | `/api/salas/codigo/{codigo}/entrar` | logado | Entra na sala; JOGADOR vira participante das tarefas não estimadas |
| GET | `/api/salas/minhas` | ADMIN | Salas que modera, ativas e inativas, com os membros; reativa as inativas que ainda têm tarefas pendentes |
| GET | `/api/salas/{salaId}/online` | logado | Quem está conectado no WebSocket da sala (em qualquer nó) |
| DELETE | `/api/salas/{salaId}` | moderador | Desativa sala |
| POST | `/api/salas/{salaId}/reativar` | moderador | Reativa uma sala desativada |
| GET | `/api/salas/{salaId}/tarefas` | logado | Lista tarefas da sala |
| POST | `/api/salas/{salaId}/tarefas` | moderador | Cria tarefa `{ "numero", "titulo", "descricao", "sprint" }` |
| POST | `/api/salas/{salaId}/tarefas/importar` | moderador | Importa várias: lista JSON ou CSV (`Content-Type: text/csv`) |
| POST | `/api/salas/{salaId}/tarefas/importar-redmine` | moderador | Importa os chamados da consulta do Redmine configurada (sem corpo) |
| POST | `/api/salas/{salaId}/tarefas/{id}/enviar-redmine` | moderador | Grava a estimativa final no chamado (horas no Tempo estimado, pontos no campo Planning (27), horas de teste no EstimativaTeste (66), situação Produto Backlog (55) → Aguardando Desenvolvimento (8); ids fixos no `RedmineService`) e confere relendo o chamado. Só tarefas importadas do Redmine; o front chama logo depois de finalizar |
| POST | `/api/salas/{salaId}/tarefas/{id}/atualizar-redmine` | moderador | Relê o chamado no Redmine e grava na tarefa o título, a descrição e os campos como estão agora (o painel de detalhe de uma tarefa estimada chama ao abrir) |
| GET | `/api/salas/{salaId}/tarefas/{id}/anexos-redmine/{nome}` | moderador | Imagem anexada ao chamado (a descrição cita `!nome.png!`), baixada do Redmine configurado com a chave do moderador. Só imagens (sem SVG), até 10 MB |
| GET / PUT | `/api/redmine/configuracao` | ADMIN | Consulta e acesso ao Redmine de quem está logado `{ "url": "<link da consulta>", "projeto", "chaveApi" }` (a chave nunca volta) |
| POST | `/api/redmine/configuracao/testar` | ADMIN | Confere a chave, o projeto e quantos chamados a consulta traz |
| POST | `/api/salas/{salaId}/tarefas/{id}/liberar` | moderador | Libera para votação (uma por vez por sala); aceita tarefas na fila ou puladas |
| POST | `/api/salas/{salaId}/tarefas/{id}/pular` | moderador | Encerra sem estimativa (`PULADA`), da fila ou em votação |
| POST | `/api/salas/{salaId}/tarefas/{id}/finalizar` | moderador | Finaliza `{ "pontosFinais": 5, "horasFinais": 6, "horasTesteFinais": 4 }` (horas opcionais) |
| DELETE | `/api/salas/{salaId}/tarefas/{id}` | moderador | Exclui tarefa (e o histórico de votos) |
| GET | `.../tarefas/{id}/votacao` | logado | Votos da rodada atual (com ocultação) e resumos |
| POST | `.../tarefas/{id}/votacao/pontos` | JOGADOR | Vota `{ "pontos": 5 }` (1, 2, 3, 5, 8, 13, 21 ou 0 = café) |
| POST | `.../tarefas/{id}/votacao/horas` | JOGADOR | Vota `{ "horas": 4 }` (1 a 16) |
| POST | `.../tarefas/{id}/votacao/revelar-pontos` | moderador | `PONTOS` → `PONTOS_REVELADOS` |
| POST | `.../tarefas/{id}/votacao/liberar-horas` | moderador | `PONTOS_REVELADOS` → `HORAS` |
| POST | `.../tarefas/{id}/votacao/revelar-horas` | moderador | `HORAS` → `HORAS_REVELADAS` |
| POST | `.../tarefas/{id}/votacao/nova-rodada` | moderador | Volta para `PONTOS` com `rodada + 1` (e fecha a trilha de teste) |
| POST | `.../tarefas/{id}/votacao/liberar-horas-teste` | moderador | Abre a trilha de horas de teste (em qualquer fase) |
| POST | `.../tarefas/{id}/votacao/horas-teste` | TESTE | Vota `{ "horas": 3 }` (1 a 16) |
| POST | `.../tarefas/{id}/votacao/revelar-horas-teste` | moderador | `VOTACAO` → `REVELADA` |
| GET | `.../tarefas/{id}/participantes` | logado | `[{ usuario, ativo, online }]`, inclusive os removidos |
| DELETE | `.../tarefas/{id}/participantes/{usuario}` | moderador | Remove da votação (descarta o voto da rodada atual) |
| POST | `.../tarefas/{id}/participantes/{usuario}` | moderador | Readmite, ou inclui um JOGADOR não vinculado |

"Moderador" = perfil ADMIN **e** criador da sala.

Erros sempre voltam no formato `ErroDTO`: 400 (validação, com `detalhes`), 401 (não autenticado), 403 (sem permissão),
404 (não encontrado), 409 (voto duplicado), 422 (regra de negócio).

## Votação

```
liberar ─► PONTOS ──revelar-pontos──► PONTOS_REVELADOS ──liberar-horas──► HORAS ──revelar-horas──► HORAS_REVELADAS ──► finalizar
              ▲                                                                                          │
              └─────────────────────────────── nova-rodada (de qualquer fase) ◄──────────────────────────┘
```

As regras vêm do Planning original, com algumas brechas fechadas:

| Regra | Planning original | Aqui |
|---|---|---|
| Quem vota | o `participante` vem no corpo da requisição | sempre o usuário do token (não dá para votar por outra pessoa) |
| Quem revela / libera horas / abre nova rodada | qualquer usuário logado | só o moderador da sala |
| Votar de novo na mesma rodada | 409 nos pontos; horas sobrescrevem | 409 nos dois (para mudar, nova rodada) |
| Carta café | `pontos = 0` | igual |
| Entrou já na fase de horas | cria o voto com café nos pontos | igual |
| Rodadas anteriores | ficam no banco | igual (histórico por `rodada`) |

**Ocultação:** até a fase ser revelada, cada jogador vê o próprio voto e, dos outros, só *se* já votaram
(`votouPontos`/`votouHoras`). OBSERVADOR e TESTE veem tudo, como no original. O moderador também não vê antes de revelar.

**Resumo** (só depois de revelar): média, mínimo, máximo, quantidade de cafés, `consenso` (todos na mesma carta)
e `sugestao` (carta de Fibonacci mais próxima da média; em empate, a maior). O café fica fora das estatísticas.
O moderador usa o resumo para decidir os `pontosFinais`/`horasFinais` ao finalizar.

As regras puras (`Cartas`, `ResumoVotacao`, `Progresso`) ficam separadas do EJB e têm testes unitários sem container.

### "Todos votaram"

Cruza **quem está conectado no WebSocket da sala** com quem já votou na fase aberta, como o
`participantesOnlineDaTarefa()` do original:

- Votação principal: espera os **participantes ativos** da tarefa que estão online, na fase `PONTOS` ou `HORAS`.
- Horas de teste: espera os **TESTEs** online, enquanto a trilha está em `VOTACAO`.
- Em `GET .../votacao`: `aguardando` / `todosVotaram` e `aguardandoTeste` / `todosTestadoresVotaram`
  (nulos quando a fase não está aberta para voto). O front pode mostrar "aguardando ana, bia".
- Pelo WebSocket, `TODOS_VOTARAM` / `TODOS_TESTADORES_VOTARAM` saem **uma vez**, na transição para completo:
  - no voto que completa a fase, logo depois do `VOTO_REGISTRADO` (os dois vão num `LoteEventosSala` para
    garantir a ordem; dois eventos CDI `AFTER_SUCCESS` separados chegavam invertidos);
  - quando o último pendente **fecha a conexão** sem votar (`VotacaoService.aoSairDaSala`);
  - ou quando o moderador **remove** o último pendente, logo depois do `PARTICIPANTE_REMOVIDO`.
- Quem votou e saiu continua contando. Quem nunca entrou não trava a rodada. É preciso pelo menos um voto.
- A presença fica em `PresencaSala` (camada service, `@ApplicationScoped`) porque o EJB precisa dela.
  O WAR só informa entradas e saídas. Várias abas do mesmo usuário contam como uma.
- Os dados ficam no cache `presenca` do Infinispan (`cache-container=planning`, criado pelo
  `docker/configurar-wildfly.cli`): `local-cache` num nó só, `replicated-cache` em cluster. O código é o mesmo.
  - Chave `sala|usuario|nó`, valor `papeis;conexoes`: cada nó só escreve as próprias chaves, sem disputa entre nós.
  - Um nó que cai sem avisar não deixa ninguém "online" para sempre: toda leitura ignora (e apaga) as entradas de
    nós que não estão mais no cluster.
  - O módulo do Infinispan é liberado para o `planning-service` no `jboss-deployment-structure.xml` do EAR.
- É só um aviso: o moderador continua decidindo quando revelar.
- O cálculo fica em `AcompanhamentoVotacao`, um bean CDI comum (não EJB) usado pelo `VotacaoService` e pelo
  `ParticipanteService`; ele roda na transação de quem chama.

### Participantes

Como o `TaskParticipant` do original: só **participantes** votam nos pontos e horas.

- `POST /salas/codigo/{codigo}/entrar` registra o usuário como membro da sala (idempotente). Um JOGADOR vira
  participante de todas as tarefas não estimadas da sala. Diferente do original, **tarefas criadas depois**
  também incluem os JOGADORes que já são membros.
- Quem não entrou na sala recebe 403 ("Entre na sala pelo código primeiro"); quem foi removido, 403
  ("Você foi removido desta votação").
- O moderador remove (`DELETE .../participantes/{usuario}`): o voto da rodada atual é descartado e o
  "todos votaram" é recalculado. Útil quando alguém sai da reunião e trava a rodada.
- A remoção é lógica (`removidoEm`): a lista mostra quem foi removido e o moderador pode **readmitir**
  (`POST .../participantes/{usuario}`), o que o original não tinha. O mesmo endpoint inclui um JOGADOR não vinculado.
- TESTE e OBSERVADOR não são participantes (a trilha de teste espera todos os TESTEs online, como no original).
- Participantes só mudam enquanto a tarefa não foi estimada.

### Pular tarefa

```
FILA ──liberar──► LIBERADA ──finalizar──► ESTIMADA
  │                  │
  └──pular──► PULADA ◄──pular
                │
                └──liberar (rodada + 1)──► LIBERADA
```

- O original tem uma contradição: a mensagem diz "Tarefa devolvida para a fila", mas o código marca
  `estimada = true, pulada = true` (sai da fila e as estatísticas contam "puladas" à parte). Aqui segue o código:
  a tarefa é **encerrada sem estimativa**, num status próprio `PULADA`, e a sala fica livre para liberar outra.
- Para "voltar para a fila", o moderador **libera a tarefa pulada de novo**: a votação recomeça numa rodada nova.
- Diferente do original, os votos não são apagados: ficam no histórico da rodada em que a tarefa foi pulada.
- Vale para tarefas na fila ou em votação; tarefa estimada ou já pulada → 422.
- Na época do `hbm2ddl=update`, o `PULADA` exigia um SQL manual em bancos existentes (o `update` não altera o
  `CHECK` que o Hibernate cria para o enum). Hoje isso é feito pelas migrations 002 e 003 (ver "Migrations").

### Importar tarefas

Um endpoint, dois formatos. O JAX-RS escolhe o método pelo `Content-Type` (dois métodos com o mesmo `@Path` e
`@Consumes` diferentes):

```bash
# JSON: a mesma lista que o front do original enviava
curl -X POST $API/salas/1/tarefas/importar -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '[{"numero":"PP-1","titulo":"Login"},{"numero":"PP-2","titulo":"Cadastro","descricao":"..."}]'

# CSV: no original o front lia o arquivo com PapaParse; aqui o servidor aceita o arquivo direto
curl -X POST $API/salas/1/tarefas/importar -H "Authorization: Bearer $TOKEN" -H "Content-Type: text/csv" \
     --data-binary @tarefas.csv
```

```
numero;titulo;descricao;sprint
PP-1;Login;Tela de login com SSO;S1
PP-2;Relatório de vendas;"Filtros: data; vendedor";S1
```

- **CSV:** cabeçalho obrigatório com `numero` e `titulo` (`descricao` e `sprint` opcionais; outras colunas são
  ignoradas). Nomes sem diferenciar maiúsculas nem acentos (`Número` vale). Separador `;` (Excel pt-BR), `,` ou tab,
  detectado pelo cabeçalho. Aspas no padrão RFC 4180 (separador, quebra de linha e `""` dentro do campo) e BOM do
  Excel são tratados. O leitor (`LeitorCsv`) é próprio e pequeno, para não trazer dependência ao EAR.
- **Tudo ou nada:** se alguma linha for inválida, nada é importado e a resposta é **400** com um detalhe por linha
  (`"Linha 3: o número é obrigatório"`), para corrigir o arquivo numa passada só. Máximo de 500 por importação.
- **Duplicadas** (número que já existe na sala, ou repetido no arquivo) são ignoradas, como no original, e listadas
  em `ignoradas`. Resposta: `{ mensagem, importadas: [TarefaDTO], ignoradas: ["PP-1"] }`.
- As tarefas entram na fila e os JOGADORes que já entraram na sala viram participantes, como no original.
- A sala recebe **um** `TAREFAS_IMPORTADAS`, e não um evento por tarefa.

### Importar do Redmine

Cada ADMIN configura na tela `/redmine` (botão "⚙ Redmine" em Minhas salas, ou o link no card de importação) o
link da consulta com os chamados a estimar, o projeto (opcional) e a chave da API (*Minha conta → Chave de acesso à
API*). Na tela da sala, o botão "Importar do Redmine" traz os chamados dessa consulta.

- **O link:** o da lista de chamados como aparece no navegador, `https://redmine/projects/{projeto}/issues?...` ou
  `https://redmine/issues?...`, com os filtros ou o `query_id` de uma consulta salva (o link do `.csv` também vale).
  É conferido ao salvar. O endereço do Redmine é o que vem antes de `/projects` ou `/issues`. O `ConsultaRedmine`
  troca por `/issues.json`, tira a paginação do link (`page`, `per_page`...) e busca de 100 em 100. No link sem
  projeto, o projeto configurado entra como `project_id`.
- **Mapeamento:** número = id do chamado, título = assunto, descrição = descrição, sprint = versão (cortada em 50
  caracteres). As colunas da fila (`dadosExtras`) usam os nomes da exportação CSV do Redmine: Título, Tipo, Situação,
  Prioridade, Atribuído para, Versão e Tempo estimado.
- **Regras:** as mesmas da importação por arquivo (tudo ou nada, duplicadas ignoradas, máximo de 500). Consulta com
  mais de 500 chamados → 422 antes de buscar o resto.
- **Chave:** gravada no banco (`configuracoes_redmine`, uma por usuário) e nunca devolvida ao cliente, só os 4
  últimos caracteres. Em branco ao salvar, mantém a atual. O Redmine aplica as permissões de quem gerou a chave.
- "Testar conexão" mostra o dono da chave, o projeto e quantos chamados a consulta traz hoje.
- Erros do Redmine viram 422 com o motivo (chave recusada, sem permissão, link não encontrado, servidor fora do ar).

## Estatísticas

`GET /api/estatisticas?salaId=&sprint=&de=2026-10-01&ate=2026-10-31` (todos opcionais), para **ADMIN** (as salas
que modera) e **SUPER** (todas). Porte do `EstatisticasService` do original, com os **mesmos nomes de campos**, para a
página `/estatisticas` do Angular servir com poucas mudanças.

| Bloco | O que traz |
|---|---|
| `resumo` | estimadas, puladas, votos, participantes, total/média de pontos, horas e horas de teste, média de rodadas, % de consenso, duração média |
| `porSprint` | o mesmo, agrupado por sprint (`"Sem sprint"` quando vazia), com as puladas de cada uma |
| `porUsuario` | votos, cafés, médias, `desvioMedioPontos` (+ estima acima, − abaixo) e `taxaAcertoModa` (% de votos iguais à carta final) |
| `distribuicaoPontos` / `distribuicaoHoras` | quantos votos em cada carta / quantidade de horas, em ordem numérica |
| `tarefas` | uma linha por tarefa estimada (moda, média, mín., máx., valores finais, rodadas, consenso, duração), da mais recente |
| `salas` / `sprints` | opções para os filtros |

As regras, iguais às do original:

- Contam só tarefas **encerradas**: as estimadas entram nos números; as puladas, só na contagem `tarefasPuladas`.
- Só os votos da **rodada final** contam; as rodadas anteriores são histórico.
- Totais e médias usam os **valores finais do moderador**; sem eles, a média dos votos. O café (0) fica fora das médias.
- Moda: em empate, a maior carta (estimativa mais conservadora).
- `de`/`ate` são dias no fuso `America/Sao_Paulo`, aplicados à data de encerramento; `ate` inclui o dia inteiro.

Diferenças em relação ao original:

- Campos novos de horas de teste no resumo e por sprint (`totalHorasTeste`, `mediaHorasTeste`).
- Sem tarefas "legadas" (aqui toda tarefa tem sala), então não há `salaId=0`.
- Data inválida ou `de` > `ate` → 400 com o motivo.

Para isso, a `Tarefa` ganhou `sprint` (no cadastro, na importação e no `TarefaDTO`), `liberadaEm` e `encerradaEm`.
O cálculo fica em `CalculadoraEstatisticas` (pura, com testes); o `EstatisticasService` só faz duas consultas: as
tarefas encerradas e os votos da rodada final (`e.rodada = t.rodadaAtual`, direto no JPQL).

> **JPQL estrito no WildFly:** o WildFly liga `hibernate.jpa.compliance.query`, então construções que o Hibernate
> aceita fora da especificação falham em tempo de execução. Exemplo encontrado aqui: alias em `JOIN FETCH`
> (`JOIN FETCH t.sala s`) dá *"Encountered aliased fetch join, but strict JPQL compliance was requested"*.
>
> **Records e o Yasson (JSON-B do WildFly):** um record de entrada com mais de um construtor quebra a desserialização
> (*"Cannot create instance of a record ... Multiple constructors found"*), também só em tempo de execução. O
> `DesserializacaoRequestsTest` (no `planning-dto`, com o mesmo Yasson 3.0.4 do WildFly) desserializa todos os
> `*Request` para pegar isso no build.

### Horas de teste (perfil TESTE)

Trilha **paralela e independente** da votação principal, como no Planning original: o moderador pode liberá-la
em qualquer fase (inclusive enquanto os jogadores ainda votam nos pontos).

```
(não liberada) ──liberar-horas-teste──► VOTACAO ──revelar-horas-teste──► REVELADA
       ▲                                                                     │
       └───────────────────────── nova-rodada ◄──────────────────────────────┘
```

- Só o perfil **TESTE** vota (o JOGADOR recebe 403), uma vez por rodada (409 se repetir), de 1 a 16 horas.
- Para revelar, pelo menos um testador precisa ter votado.
- Em `GET .../votacao`: `faseHorasTeste`, `votosTeste` (`usuario`, `votou`, `horas`) e, depois de revelar,
  `resumoHorasTeste` (média, mínimo, máximo).
- **Ocultação:** até revelar, cada testador vê só o próprio voto; dos demais, só *se* votaram. Diferente do original,
  onde o ADMIN via os valores antes de revelar: aqui ninguém vê, igual à votação principal.
- O voto do testador fica na mesma tabela `estimativas` (coluna `horas_teste`); o service separa as listas
  pelos campos preenchidos.

### Exemplo com curl

```bash
API=http://localhost:8082/planning/api
curl -X POST $API/auth/registrar -H "Content-Type: application/json" \
     -d '{"usuario":"jovio","senha":"segredo123","perfil":"ADMIN"}'
TOKEN=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
     -d '{"usuario":"jovio","senha":"segredo123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -X POST $API/salas -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"nome":"Sprint 42"}'
```

## Migrations (Liquibase)

O schema é do Liquibase; o Hibernate não mexe mais nele (`hibernate.hbm2ddl.auto=none`).

```
planning-persistence/src/main/resources/db/changelog/
├── db.changelog-master.xml              inclui os arquivos abaixo, na ordem
└── changes/
    ├── 001-esquema-inicial.xml          todas as tabelas, FKs, uniques e índice
    ├── 002-remover-checks-do-hibernate.xml   só Postgres: tira os CHECK sem nome que o hbm2ddl criou
    └── 003-checks-dos-enums.xml         CHECK com nome para cada coluna de enum
```

- **Quando roda:** no deploy, pelo EJB `MigracaoBanco` (`@Singleton @Startup`), antes de qualquer outro código usar o
  banco. O `CriacaoSuperUsuario` declara `@DependsOn("MigracaoBanco")`. Se uma migration falhar, o deploy falha.
- **Transação:** o `MigracaoBanco` é `@TransactionManagement(BEAN)`. O Liquibase faz commits na conexão, e numa
  transação JTA do container isso é proibido.
- **Vários servidores:** a tabela `DATABASECHANGELOGLOCK` garante que só um aplique as migrations; os outros esperam.
- **Bancos criados pelo `hbm2ddl`** (ex.: o volume do docker-compose de versões anteriores): o 001 tem a precondição
  "tabela `usuarios` não existe" e, se já existir, é só marcado como aplicado (`MARK_RAN`). O 002 troca os `CHECK`
  automáticos do Hibernate (que impediam o `PULADA`) pelos do 003, com nome e controlados por migration.
- **Enum com valor novo:** crie um changeset que faça `DROP CONSTRAINT ck_...` e `ADD CONSTRAINT ck_...` com a lista
  nova. O `addCheckConstraint` do Liquibase é só da versão paga, por isso os checks estão em `<sql>`.
- **Nunca edite um changeset já aplicado:** o Liquibase guarda o checksum e o próximo deploy falha. Crie outro.

**Validação no build** (`MigracoesTest`, no `planning-persistence`): aplica o changelog num H2 em memória e sobe o
Hibernate com `hbm2ddl=validate`, com as entidades do `persistence.xml` e as mesmas versões de Hibernate (7.4.5) e
H2 do WildFly 41. Mudou uma entidade e esqueceu a migration, o build quebra (*"Schema validation: missing column
[apelido] in table [usuarios]"*). Também testa a reaplicação (não faz nada), os checks dos enums e a adoção de um
banco criado pelo `hbm2ddl`. O `validate` não fica no `persistence.xml` porque o Hibernate valida ao subir o PU,
antes de a migration rodar.

Ele já pegou uma diferença real: `TEXT` no H2 vira `CLOB` pelo Liquibase, e o Hibernate esperava texto comum. Por
isso o changelog tem a propriedade `${tipo.texto}` (`TEXT` no Postgres, `VARCHAR` no H2).

Versão: Liquibase **4.33.0** (última da linha 4.x, Apache 2.0). A 5.0 anunciou troca de licença; confira antes de
subir. O `jaxb-api` (javax.*) que vem junto foi excluído do EAR: o changelog em XML não precisa dele.

## Testes

| Comando | O que roda | Tempo |
|---|---|---|
| `mvn package` | Testes unitários (regras puras, JWT, desserialização dos records, migrations num H2) | segundos |
| `mvn verify -Pintegracao` | Os unitários + os testes de integração do `planning-it` | ~1–2 min (+ download do WildFly na 1ª vez) |

### Testes de integração (`planning-it`)

Caixa preta: chamam a API por HTTP e o WebSocket de verdade, contra o `planning.ear` implantado num WildFly 41
descartável. Testam o que o teste unitário não alcança: o deploy do EAR, as migrations no startup, o Elytron com o
mecanismo JWT, as transações JTA, os eventos CDI `AFTER_SUCCESS` chegando pelo WebSocket, o JSON-B serializando os
records e o CORS.

O Maven faz o ciclo inteiro (`planning-it/pom.xml`):

1. `pre-integration-test`: baixa e descompacta o `wildfly-dist` 41.0.1 em `planning-it/target` (só na primeira vez);
   roda o `src/test/wildfly/configurar.cli` com o servidor desligado (datasource H2 e `integrated-jaspi=false`);
   sobe o WildFly com offset 300 (HTTP 8380, gerência 10290) e as variáveis de produção (`SUPER_*`, `JWT_SECRET`,
   `ALLOWED_ORIGINS`); implanta o EAR com o `wildfly-maven-plugin` (`deploy-artifact`), que falha se o deploy falhar.
2. `integration-test`: o failsafe roda os `*IT.java`.
3. `post-integration-test`: desliga o servidor, mesmo com teste falhando (o failsafe só reprova o build no `verify`).

| Classe | Cobre |
|---|---|
| `AutenticacaoIT` | 401 com `WWW-Authenticate` e CORS, preflight, login/me, token adulterado, 403, SUPER criado no deploy |
| `VotacaoIT` | Pontos → horas → horas de teste → finalizar, ocultação por perfil, 409, nova rodada, participantes |
| `TempoRealIT` | Token inválido (1008), origem recusada, ordem `VOTO_REGISTRADO` → `TODOS_VOTARAM`, voto sem valor no evento |
| `ImportacaoEstatisticasIT` | CSV do Excel e JSON, tudo ou nada, e estatísticas só da rodada final e das salas do ADMIN |
| `GestaoUsuariosIT` | Desativar e mudar perfil valendo para tokens já emitidos, exclusão com histórico, SUPER intocável |

Todos os testes compartilham o servidor, então cada um cria os próprios usuários (nomes únicos) e o próprio ADMIN,
pelo SUPER (`Cenario`). Assim não dependem de ordem. Por que não Arquillian? Ele permite testes dentro do container
(injetar EJB), mas exige um adaptador por versão de servidor; para validar o comportamento que o front vê, a API
por fora é suficiente e não amarra os testes à versão do WildFly. Testcontainers ficaria para quando houver Docker
(aí o datasource seria o Postgres de verdade).

## Próximos passos sugeridos

- **Testes de integração com Postgres:** hoje o `planning-it` usa o H2 do WildFly. Com Docker, um Testcontainers
  de Postgres no lugar do H2 testaria também a migration 002 (só Postgres) e os tipos reais.
- **Estatísticas no banco:** hoje as tarefas encerradas do escopo são carregadas e agregadas em memória (como no
  original). Com muito histórico, filtros e agregações iriam para o JPQL (`GROUP BY`) ou para uma view.

## Compilar

Na hospedagem local, dentro de `hospedagem-local/` (detalhes no [HOSPEDAGEM-LOCAL.md](HOSPEDAGEM-LOCAL.md)):

```powershell
.\implantar.ps1
```

## Iniciar

```powershell
.\IniciarServidor.bat
```
 
