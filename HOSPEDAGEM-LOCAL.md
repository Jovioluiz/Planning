# Hospedagem na própria máquina (Windows, sem Docker)

Roda o planning-jakarta como hoje roda o Planning original: na sua máquina, publicado pelo Cloudflare Tunnel
(`chutometro.dev.br` e `api.chutometro.dev.br`). Sem custo de hospedagem e sem Docker.

```
navegador ──► Cloudflare ──► cloudflared (serviço do Windows, já instalado)
                              ├─ chutometro.dev.br      ──► 127.0.0.1:4200  front (Node, Angular SSR)
                              └─ api.chutometro.dev.br  ──► 127.0.0.1:8082  WildFly ──► PostgreSQL 17 (5433)
```

| Peça | Onde | Observação |
|---|---|---|
| PostgreSQL 17 | serviço `postgresql-x64-17`, porta 5433 (já instalado) | O mesmo servidor do original; o banco novo é `planningjakarta` |
| WildFly 41 | `%USERPROFILE%\planning-servidor\wildfly-41.0.1.Final` | Só escuta em 127.0.0.1 (quem chega de fora é o cloudflared) |
| Front | `%USERPROFILE%\planning-servidor\frontend` | Build de produção (`--configuration chutometro`), não o `ng serve` |
| Logs | Ao vivo nas janelas "Planning - WildFly (backend)" e "Planning - Front", com os erros em vermelho e os avisos em amarelo. O backend registra cada requisição (`POST /salas/14/tarefas/3/liberar -> 200 (35 ms) [ADM]`), com a mensagem de erro que o front recebeu nos 4xx, além das entradas e saídas do WebSocket e da inativação de salas. O ERROR que o WildFly gera em todo acesso negado aparece só como uma linha cinza (`NEGADO`) | Cada erro, com o stack trace, também vai para `logs\erros-backend.log` / `logs\erros-front.log`; o log completo do WildFly fica em `wildfly-41.0.1.Final\standalone\log\server.log` |
| Backups | `%USERPROFILE%\planning-servidor\backups` | `pg_dump` diário, 14 dias |

Os scripts estão em [`hospedagem-local/`](hospedagem-local). Rode-os num PowerShell dentro dessa pasta; se o
PowerShell recusar scripts, use `powershell -ExecutionPolicy Bypass -File .\instalar.ps1` (ou os `.bat`).

## Primeira vez (troca do original por este)

1. **Instalar** (baixa o WildFly pelo Maven, configura, cria o banco vazio e compila backend e front; nada é iniciado):
   ```powershell
   .\instalar.ps1
   ```
   Na primeira execução ele cria o `config.ps1` (fora do git) com um `JWT_SECRET` aleatório. Confira a senha do
   Postgres nele (`PgSenha`) se não for `postgres`.
2. **Simular a migração** (lê o `planningdb`, faz tudo no `planningjakarta` e desfaz; mostra o relatório):
   ```powershell
   .\migrar.ps1 -Simular
   ```
3. **Parar o Planning original**: feche as janelas "Backend" e "Frontend" do `IniciarServidor.bat` antigo.
4. **Migrar de verdade** (faz um backup antes; o `planningdb` não é alterado):
   ```powershell
   .\migrar.ps1
   ```
5. **Iniciar**:
   ```powershell
   .\iniciar.ps1          # ou duplo clique em IniciarServidor.bat
   ```
6. **Cloudflare** (no painel: Zero Trust → Networks → Tunnels → o seu tunnel → Public hostnames). O cloudflared
   desta máquina usa token, então as rotas ficam no painel, não num arquivo local:
   - `api.chutometro.dev.br` → `http://127.0.0.1:8082` (era a 8081)
   - `chutometro.dev.br` → `http://127.0.0.1:4200` (a mesma porta de antes; use `127.0.0.1` em vez de `localhost`)
7. **Conferir**: em `https://chutometro.dev.br`, entre com um usuário antigo e a senha de sempre.
8. **Backup diário**: a tarefa "Planning Jakarta - backup diario" do Agendador roda às 12h (se a máquina estiver
   desligada nesse horário, roda quando ligar). Além dela, o `iniciar.ps1` faz um backup sempre que o último tiver
   mais de 20 h.

A inicialização é manual: depois de ligar ou reiniciar a máquina, rode `.\iniciar.ps1` (ou `IniciarServidor.bat`
desta pasta, não o do Planning original). O `registrar-inicializacao.ps1` também cria um atalho na pasta
Inicializar do Windows, que faz tudo subir sozinho ao entrar; se rodar esse script para recriar o backup, apague
depois o atalho `Planning Jakarta` em `shell:startup`. `.\registrar-inicializacao.ps1 -Remover` tira o atalho
e o backup agendado.

**Para voltar ao original:** `.\parar.ps1`, aponte `api.chutometro.dev.br` de volta para a 8081 e rode o
`IniciarServidor.bat` antigo. O `planningdb` continua como estava na migração: o que foi votado depois, no
planning-jakarta, não volta para ele.

## Dia a dia

| Para | Rode |
|---|---|
| Iniciar / parar | `.\iniciar.ps1` / `.\parar.ps1` (ou `IniciarServidor.bat` / `PararServidor.bat`). Abre uma janela do cmd para o WildFly e outra para o front; o `parar.ps1` fecha as duas. Fechar uma janela no X derruba aquele servidor na hora (prefira o `parar.ps1`, que desliga o WildFly direito) |
| Publicar uma mudança no código | `.\implantar.ps1` (o WildFly troca o EAR sem reiniciar; o front reinicia sozinho). `-SoBackend` pula o front; `-ComTestes` roda os testes |
| Backup agora | `.\backup.ps1` |
| Restaurar um backup | `.\parar.ps1`, depois `pg_restore -h localhost -p 5433 -U postgres -d planningjakarta --clean --if-exists <arquivo.dump>` |
| Ver o que deu errado | `.\erros.ps1`: mostra os 5 últimos erros (dos dois) e **copia para a área de transferência**, pronto para colar. `-Quantidade 10` muda quantos; `-Limpar` apaga o histórico depois de corrigir. Se um servidor cair, a janela dele fica aberta com a mensagem |
| Console do WildFly | `http://127.0.0.1:9992` (precisa de um usuário: `wildfly-41.0.1.Final\bin\add-user.bat`) |

Tudo vem do `config.ps1`: portas, senha do banco, `ALLOWED_ORIGINS`, `SUPER_SENHA`, tempo de inativação das
salas, memória do WildFly e dias de backup. Depois de mudar, `.\parar.ps1` e `.\iniciar.ps1`.

## Limites (os mesmos de hoje)

- Só funciona com a máquina ligada e com internet, e depois de rodar o `iniciar.ps1` (nada sobe sozinho ao ligar a
  máquina, como no `IniciarServidor.bat` do original).
- Memória: o WildFly usa uns 620 MB; o front, uns 100 MB.
- O backup fica no mesmo disco. De vez em quando, copie a pasta `backups` para outro lugar (um pendrive ou a nuvem).

## Testado nesta máquina

Numa instalação paralela (outra pasta, banco `planningjakarta_teste`, portas 8083/4300, tudo removido depois):
`instalar.ps1` do zero; `migrar.ps1 -Simular` e `migrar.ps1` com o **`planningdb` real** no PostgreSQL 17
(28 usuários, 9 salas, 194 tarefas, 196 estimativas, 754 participações, 40 membros, nenhum aviso); `iniciar.ps1`
(no ar em ~40 s), a API servindo as salas migradas, o CORS para `https://chutometro.dev.br`, `implantar.ps1` com
tudo no ar e `parar.ps1`.

Depois, na instalação de verdade (21/10/2026): migração do `planningdb` com os mesmos números, API, CORS e WebSocket
pelo `api.chutometro.dev.br`, login de um usuário antigo com a senha de sempre e a tarefa de backup diário rodada
uma vez.
