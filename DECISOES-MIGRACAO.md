# Decisões da migração (diferentes do Planning original)

| # | Assunto | Original | Nesta versão | Por quê |
|---|---|---|---|---|
| 1 | Senhas | BCrypt (Spring) | PBKDF2; hashes BCrypt migrados são aceitos no login e regravados em PBKDF2 (`SenhaLegada`) | Hash não se converte; ninguém precisa trocar senha |
| 2 | Login que cadastra | `POST /auth/login` criava o usuário | Backend separa login e registrar; o **front** tenta login e, se falhar, registra (mesma experiência) | Mantém a API limpa sem mudar a UX |
| 3 | Nome de usuário | Qualquer texto, em maiúsculas | Padrão relaxado para letras (com acento), números, espaço, `.`, `_`, `-`; o front continua em maiúsculas e faz `trim` da senha | Usuários migrados precisam continuar válidos |
| 4 | Inativação de sala | Automática 15 s após o moderador desconectar; reativação automática ao listar | **Igual ao original** (resposta da dúvida 2), e ainda: vale em cluster (a presença é distribuída), `SALA_INATIVACAO_SEGUNDOS` muda a espera (0 desliga), e há `DELETE /salas/{id}` e `POST /salas/{id}/reativar` explícitos | — |
| 5 | Tarefas sem sala (legado) | Funcionavam fora de sala | Migradas para a sala "Backlog (migrado do Planning original)", moderada pelo 1º ADMIN (ou `--moderador-legado`) | Toda tarefa pertence a uma sala aqui |
| 6 | `user_sprints` / tela de sprint | Existia (não roteada) | Não migrado | Funcionalidade já desativada no original |
| 7 | "Resetar votação" | Apagava votos | Vira nova rodada (votos antigos ficam no histórico) | Histórico preservado para estatísticas |
| 8 | Horas | `Double` | Inteiro (arredondado na migração e ao finalizar) | Votos sempre foram 1–16 inteiros |
| 9 | Finalizar | Valores opcionais | Valores opcionais (o `@NotNull` de pontos foi retirado) | Alinhado ao original |
| 10 | Horas de teste antes de revelar | ADMIN via | Ninguém vê (🔒) | Decisão já tomada nesta versão |
| 11 | Rodada anterior | `listar` devolvia todas as rodadas | `votosRodadaAnterior` só para moderador/OBSERVADOR/TESTE | Evita ancorar o voto do jogador |
| 12 | `prioridade`/`status` da tarefa | Colunas próprias | Vão para `dadosExtras` na migração | Sem equivalente; nada se perde |
| 13 | WebSocket | STOMP/SockJS, tópicos | WebSocket nativo por sala; o front traduz os eventos para o formato antigo | Componentes quase sem mudança |
| 14 | Presença | `POST /sessoes/sala/{id}` | Estar conectado no WebSocket da sala | Presença distribuída (Infinispan) |
| 15 | Front | Chamava a API direto | Camada de adaptação nos services (`api-jakarta.ts`) | Menor risco nas telas grandes |
| 16 | Entrar na sala | Vinculava o jogador às tarefas pendentes só na primeira entrada | Completa os vínculos que faltarem a cada entrada (removidos continuam removidos) | Corrige dados migrados em que o membro não era participante de uma tarefa pendente (achado no teste de ponta a ponta) |
| 17 | URLs | `https://api.chutometro.dev.br` e o serviço do Render | Os mesmos domínios + `/planning` (context root do EAR); o tunnel passa a apontar para a 8082 | Resposta da dúvida 1 |
| 18 | `ALLOWED_ORIGINS` | Lista do `application.yml` | Mesma lista + `https://planning-poker-front.onrender.com` (que estava no `render.yaml`) | Resposta da dúvida 1 |
| 19 | Tela de usuários (SUPER) | Listar e excluir; não mostrava TESTE | Excluir quem não tem histórico; desativar/reativar quem tem; grupo de Testadores | Excluir com votos apagaria estatísticas |
| 20 | Sem sala | OBSERVADOR/TESTE iam para tarefas "legadas" | A sala de espera explica que é preciso entrar pelo link | Toda votação é numa sala |
| 21 | "Registrando..." travado | Erro ao votar deixava o botão travado | O erro libera o botão e mostra a mensagem do backend | Bug do original, visto no teste |
| 22 | STOMP/SockJS | Dependências do front | Removidas (`@stomp/stompjs`, `sockjs-client`) | WebSocket nativo |
| 23 | Hospedagem | `IniciarServidor.bat`: `mvn spring-boot:run` e `ng serve` em duas janelas | Scripts em `hospedagem-local/`: WildFly e front (build de produção em Node, `--configuration chutometro`) cada um na sua janela do cmd, como no original, no mesmo PostgreSQL 17 (banco `planningjakarta`); início manual (`iniciar.ps1`); backup diário | Sem custo e sem Docker (resposta da nova dúvida); `ng serve` é servidor de desenvolvimento |
| 24 | WildFly exposto | O Spring escutava em todas as interfaces | O WildFly só escuta em 127.0.0.1 (o cloudflared chega por ali); o front continua em todas, para a rede local | Menos superfície; o navegador acessa a API pelo `api.chutometro.dev.br` |
| 26 | Moderador volta a uma sala inativa | O WebSocket era global: qualquer conexão do moderador reativava as salas dele com tarefas em aberto | O WebSocket é por sala: a sala inativa aceita só o moderador e volta a ficar ativa se tem tarefas pendentes (jogador continua recusado até lá) | Achado em uso: com o servidor reiniciado e o moderador na tela da sala, ele não conseguia reconectar e os jogadores recebiam "A sala está inativa" |
| 25 | `ALLOWED_ORIGINS` na própria máquina | Lista do `application.yml` | A mesma lista, sem o front do Render (que não é usado nessa hospedagem) | O docker-compose mantém a lista com o Render |

