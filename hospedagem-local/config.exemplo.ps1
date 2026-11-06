# Configuração da hospedagem na própria máquina (Windows, sem Docker).
# O instalar.ps1 copia este arquivo para config.ps1 na primeira vez, já com um JWT_SECRET aleatório.
# Edite o config.ps1 (ele fica fora do git porque tem senhas), não este.

$Planning = @{
    # Onde ficam o WildFly, os logs e os backups. Fora do projeto, para o "mvn clean" não apagar nada.
    Servidor          = "$env:USERPROFILE\planning-servidor"

    # PostgreSQL instalado no Windows (o mesmo servidor do Planning original; o banco é outro)
    PostgresBin       = 'C:\Program Files\PostgreSQL\17\bin'
    PgHost            = 'localhost'
    PgPorta           = 5433
    PgUsuario         = 'postgres'
    PgSenha           = 'postgres'
    # Banco de produção e banco de testes, no mesmo PostgreSQL. Qual está em uso fica em <Servidor>\banco-ativo.txt;
    # para trocar: .\banco.ps1 testes  /  .\banco.ps1 producao  (ou UsarBancoTestes.bat / UsarBancoProducao.bat)
    Bancos            = @{ producao = 'planningjakarta'; testes = 'planningjakarta_testes' }
    BancoOriginal     = 'planningdb'      # origem da migração (planning-migracao)

    # WildFly: HTTP na 8080 + offset (8082) e console de gerência na 9990 + offset (9992).
    # 127.0.0.1: só a própria máquina (e o cloudflared) acessa; o navegador chega pelo api.chutometro.dev.br.
    PortaOffset       = 2
    Bind              = '127.0.0.1'
    # Heap do WildFly. Medido: ~620 MB de memória no total com a aplicação.
    MemoriaJava       = '-Xms256m -Xmx768m'

    # Front (Angular SSR em Node), na porta do front original: os dois não rodam juntos.
    FrontPorta        = 4200
    # Configuração do angular.json usada no build: "chutometro" chama https://api.chutometro.dev.br/planning
    FrontConfiguracao = 'chutometro'

    # Variáveis da aplicação (as mesmas do docker-compose.yml)
    AllowedOrigins    = 'http://localhost:4200,http://192.168.0.103:4200,http://100.99.136.57:4200,https://chutometro.dev.br'
    JwtSegredo        = '__GERADO_NO_INSTALAR__'
    JwtValidadeMin    = 480
    # Cifra a chave da API do Redmine no banco. Trocar depois obriga os moderadores a informar a chave de novo
    RedmineSegredo    = '__GERADO_NO_INSTALAR_REDMINE__'
    # Cria um SUPER só se não houver nenhum (o SUPER do original vem na migração). Vazio: não cria.
    SuperUsuario      = 'super'
    SuperSenha        = ''
    # Inativa a sala 15 s depois que o moderador sai, como no original; 0 desliga
    SalaInativacaoSeg = 15

    # E-mail do "Esqueci minha senha" (o link para criar uma senha nova). SmtpHost vazio: desligado.
    # Gmail: SmtpHost 'smtp.gmail.com', porta 587, SmtpUsuario = o seu Gmail e SmtpSenha = uma "senha de app"
    # (https://myaccount.google.com/apppasswords; precisa da verificação em duas etapas ligada).
    SmtpHost          = ''
    SmtpPorta         = 587
    SmtpUsuario       = ''
    SmtpSenha         = ''
    SmtpRemetente     = ''                # vazio: o próprio SmtpUsuario
    SmtpSsl           = $false            # $true só para SSL direto (porta 465); a 587 usa STARTTLS
    # Endereço do front que vai no link do e-mail
    FrontUrl          = 'https://chutometro.dev.br'

    # Backups diários do banco (pg_dump): quantos dias guardar
    BackupDias        = 14
}
