# Migração do Planning original (Spring Boot) para o planning-jakarta

Passo a passo para trocar o Planning original por esta versão sem perder dados. O que muda em relação ao
original está em [DECISOES-MIGRACAO.md](DECISOES-MIGRACAO.md).

## O que é migrado

| Original (`planningdb`) | Esta versão (`planningjakarta`) | Observação |
|---|---|---|
| `usuarios` | `usuarios` | Senha continua em BCrypt; vira PBKDF2 no primeiro login de cada um |
| `salas` | `salas` | Mesmos códigos: os links `/#/sala/{codigo}` continuam valendo |
| `tarefas` | `tarefas` | Flags viram `status`/`fase`; `numero` vira texto; horas arredondadas; `prioridade`/`status` vão para `dadosExtras` |
| `tarefas` sem sala | `tarefas` na sala "Backlog (migrado do Planning original)" | Moderada pelo primeiro ADMIN (`--moderador-legado=USUARIO` muda) |
| `estimativas` | `estimativas` | Todas as rodadas; voto repetido na mesma rodada fica o mais recente |
| `task_participants` | `participantes_tarefa` | Vínculos com tarefas que não existem mais ficam de fora |
| `sala_membros` | `sala_membros` | |
| `user_sprints` | — | Não existe nesta versão |

Os ids são mantidos. A ferramenta roda numa transação só: se algo falhar, o destino fica vazio.

## Passo a passo

> **Sem Docker (na própria máquina, com o PostgreSQL do Windows):** siga o [HOSPEDAGEM-LOCAL.md](HOSPEDAGEM-LOCAL.md);
> o `hospedagem-local\migrar.ps1` faz os passos abaixo (backup, simulação, migração) com o banco de lá. O passo a
> passo a seguir é o da versão com Docker.

1. **Backup do banco antigo** (sempre):
   ```bash
   docker exec planning-db pg_dump -U postgres planningdb > planningdb-$(date +%F).sql
   ```
2. **Pare o backend antigo** (o banco continua de pé), para ninguém votar durante a migração:
   ```bash
   docker compose stop backend      # no diretório do Planning original
   ```
3. **Suba só o banco novo** (vazio) e gere a ferramenta:
   ```bash
   docker compose up -d db          # neste diretório: Postgres na 5434
   mvn -pl planning-migracao -am package -DskipTests
   ```
4. **Simule** (faz tudo e desfaz no fim; mostra o relatório):
   ```bash
   ORIGEM_URL=jdbc:postgresql://localhost:5433/planningdb ORIGEM_USUARIO=postgres ORIGEM_SENHA=postgres \
   DESTINO_URL=jdbc:postgresql://localhost:5434/planningjakarta DESTINO_USUARIO=postgres DESTINO_SENHA=postgres \
   java -jar planning-migracao/target/planning-migracao.jar --simular
   ```
   Leia os avisos: tarefas legadas, votos repetidos, vínculos órfãos, mais de uma tarefa em votação na mesma sala.
5. **Migre de verdade**: o mesmo comando, sem `--simular`. O destino recebe o schema (Liquibase) e os dados.
6. **Suba o resto**:
   ```bash
   SUPER_SENHA=uma-senha-sua docker compose up -d --build
   ```
   O SUPER do original é migrado como qualquer usuário; `SUPER_SENHA` só cria um SUPER se não houver nenhum.
7. **Aponte o Cloudflare Tunnel** de `api.chutometro.dev.br` para `http://localhost:8082` (era a 8081). O front
   chama `https://api.chutometro.dev.br/planning` (o `/planning` é o context root do EAR).
8. **Confira**: entre com um usuário antigo e a senha de sempre; as salas do moderador aparecem em "Minhas salas"
   (as inativas com tarefas pendentes voltam a ficar ativas, como no original).

Para voltar atrás: pare o planning-jakarta, aponte o tunnel de volta para a 8081 e suba o backend antigo; o banco
antigo não é alterado pela migração (a origem é aberta só para leitura).

## Testado

- `MigracaoDadosTest` (H2): schema do original com casos difíceis (rodadas, pulada, em votação, legado, voto
  repetido, participante órfão, `dados_extras` inválido, usuário com acento e espaço, hash BCrypt real do Spring).
- De ponta a ponta: banco no formato do original → `planning-migracao.jar` → WildFly → front no navegador. Login
  com a senha antiga, sala inativa reativada, votação em andamento retomada, revelar, finalizar, nova tarefa.
- Não testado: contra um Postgres real (sem Docker na máquina do desenvolvimento). A migration 002 só roda em
  Postgres. Rode o passo 4 (simulação) antes do 5.
