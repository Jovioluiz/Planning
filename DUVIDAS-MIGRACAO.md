# Dúvidas da migração (responda aqui)

1. **URL de produção do novo backend** (o original usava Render e `api.chutometro.dev.br` via Cloudflare Tunnel).
   Qual URL vai em `frontend/src/environments/environment.prod.ts` e em `ALLOWED_ORIGINS`?
   Resposta: Podem ser as mesmas que eram utilizadas?

2. **Inativação automática de sala** (15 s após o moderador sair). Quer de volta ou fica só a explícita?
   Resposta: Pode colocar de volta.

3. **Sala do backlog legado:** quem deve moderar? (padrão: o primeiro ADMIN por id)
   Resposta: padrão: o primeiro ADMIN por id

4. **Hospedagem:** o `render.yaml` do original roda Spring Boot. O WildFly vai para onde (Render com Docker, VPS, outro)?
   Resposta: qual seria o melhor?

# Como as respostas foram aplicadas

1. Mesmos domínios, com o `/planning` do EAR: `environment.ts` → `https://api.chutometro.dev.br/planning`,
   `environment.prod.ts` → `https://planning-poker-api-y60o.onrender.com/planning`. `ALLOWED_ORIGINS` no
   docker-compose = lista do original + `https://planning-poker-front.onrender.com`. O Cloudflare Tunnel precisa
   apontar para a 8082 (era a 8081).
2. Inativação automática de volta, como no original (15 s; reativa ao listar "Minhas salas"). Funciona em cluster.
3. Mantido: primeiro ADMIN por id.
4. Recomendação abaixo.

## Recomendação de hospedagem (dúvida 4)

Medido: o WildFly com a aplicação (perfil full, com Artemis e Infinispan) usou ~620 MB de memória (working set),
contra ~200-300 MB típicos de um Spring Boot pequeno.

| Opção | Custo aproximado | Prós | Contras |
|---|---|---|---|
| **VPS + docker compose + Cloudflare Tunnel** (recomendado) | Oracle Cloud Always Free (ARM, até 24 GB) ou ~€4-5/mês (Hetzner CX22, 4 GB) | Sobe exatamente o docker-compose deste projeto (WildFly + Postgres + front); 
mantém `chutometro.dev.br` pelo tunnel, sem abrir portas; sempre ligado | Você cuida do servidor (atualizações, backup do Postgres) |
| Como hoje: sua máquina + Cloudflare Tunnel | Zero | Nada novo para aprender | Só funciona com a máquina ligada |
| Render (Docker) | Free tem 512 MB e dorme; o plano que comporta o WildFly folgado fica em torno de US$ 25/mês | Deploy por git push, banco gerenciado | Free não comporta; ao dormir derruba o WebSocket e o 
WildFly leva ~1 min para subir |

A minha escolha: **VPS com o docker-compose e o Cloudflare Tunnel** — é o mesmo arranjo que você já usa (tunnel),
só que numa máquina sempre ligada. Os preços são aproximados; confira antes de contratar.

Nova dúvida (opcional): quer que eu prepare os arquivos para a VPS (um `docker-compose.prod.yml` com o
`cloudflared` e backup automático do Postgres)?
   Resposta: prepara, mas sem gastar com hospedagem: fica na minha máquina, que não tem Docker (não suporta).

   Aplicado: [HOSPEDAGEM-LOCAL.md](HOSPEDAGEM-LOCAL.md) e os scripts em `hospedagem-local/` (WildFly e front
   direto no Windows, o PostgreSQL 17 e o cloudflared que já estão instalados, backup diário com `pg_dump`).
   O docker-compose continua para quem tiver Docker.

# Estado do trabalho

Feito e testado:
- Backend com tudo o que o front e os dados antigos precisam; inativação automática; 63 testes unitários e
  27 de integração passando (`mvn verify -Pintegracao`).
- `planning-migracao` (ferramenta) com 12 testes; rodada de ponta a ponta (banco no formato do original → migração
  → WildFly → front no navegador).
- Frontend migrado em `frontend/` (`ng build` sem erros, configuração `local`), no `docker-compose.yml`.
- `MIGRACAO.md` com o passo a passo.

- Hospedagem sem Docker (`hospedagem-local/`), testada numa instalação paralela, incluindo a migração do
  `planningdb` real no PostgreSQL 17 desta máquina.

Não testado (sem Docker nesta máquina): `docker compose up`.
