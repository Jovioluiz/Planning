# Funções usadas pelos outros scripts (carregado com ". $PSScriptRoot\comum.ps1").
$ErrorActionPreference = 'Stop'
# Os .bat do WildFly (standalone, jboss-cli) não param em "Pressione qualquer tecla" no fim
$env:NOPAUSE = 'true'

$Raiz = Split-Path -Parent $PSScriptRoot          # planning-jakarta
$WildFlyVersao = '41.0.1.Final'
$PostgresDriverVersao = '42.7.14'

# PLANNING_CONFIG aponta para outro arquivo de configuração (ex.: uma segunda instalação para testes)
function Arquivo-Config {
    if ($env:PLANNING_CONFIG) { return $env:PLANNING_CONFIG }
    return (Join-Path $PSScriptRoot 'config.ps1')
}

function Carregar-Config {
    $arquivo = Arquivo-Config
    if (-not (Test-Path $arquivo)) {
        throw "Falta o $arquivo. Rode antes o instalar.ps1 (ele cria a partir do config.exemplo.ps1)."
    }
    . $arquivo
    $Planning.WildFly = Join-Path $Planning.Servidor "wildfly-$WildFlyVersao"
    $Planning.Logs    = Join-Path $Planning.Servidor 'logs'
    $Planning.Backups = Join-Path $Planning.Servidor 'backups'
    $Planning.Front   = Join-Path $Planning.Servidor 'frontend'
    $Planning.PortaHttp     = 8080 + $Planning.PortaOffset
    $Planning.PortaGerencia = 9990 + $Planning.PortaOffset
    return $Planning
}

function Etapa([string] $texto) {
    Write-Host ''
    Write-Host "==> $texto" -ForegroundColor Cyan
}

# Roda um executável e para o script se ele falhar (o $ErrorActionPreference não vale para .exe).
function Executar([string] $exe, [string[]] $argumentos) {
    & $exe @argumentos
    if ($LASTEXITCODE -ne 0) {
        throw "Falhou (código $LASTEXITCODE): $exe $($argumentos -join ' ')"
    }
}

function Psql($cfg, [string] $banco, [string] $sql) {
    $env:PGPASSWORD = $cfg.PgSenha
    $saida = & (Join-Path $cfg.PostgresBin 'psql.exe') -h $cfg.PgHost -p $cfg.PgPorta -U $cfg.PgUsuario `
        -d $banco -v ON_ERROR_STOP=1 -tAc $sql
    if ($LASTEXITCODE -ne 0) { throw "psql falhou: $sql" }
    return $saida
}

function Url-Jdbc($cfg, [string] $banco) {
    return "jdbc:postgresql://$($cfg.PgHost):$($cfg.PgPorta)/$banco"
}

# Variáveis lidas pela aplicação (System.getenv) e pelo datasource (${env.DB_URL} no standalone-full.xml)
function Definir-Ambiente($cfg) {
    $env:DB_URL                   = Url-Jdbc $cfg $cfg.Banco
    $env:DB_USER                  = $cfg.PgUsuario
    $env:DB_PASS                  = $cfg.PgSenha
    $env:ALLOWED_ORIGINS          = $cfg.AllowedOrigins
    $env:JWT_SECRET               = $cfg.JwtSegredo
    $env:JWT_VALIDADE_MINUTOS     = "$($cfg.JwtValidadeMin)"
    $env:SUPER_USUARIO            = $cfg.SuperUsuario
    $env:SUPER_SENHA              = $cfg.SuperSenha
    $env:SALA_INATIVACAO_SEGUNDOS = "$($cfg.SalaInativacaoSeg)"
    # Lido pelo standalone.conf.bat do WildFly
    $env:JAVA_OPTS = "$($cfg.MemoriaJava) -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
}

function Porta-Ocupada([int] $porta) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $porta -ErrorAction SilentlyContinue)
}

# Cada servidor numa janela com título, mostrando o log ao vivo (como o IniciarServidor.bat do original), com os
# erros em vermelho e gravados em logs\<arquivoErros> (ver janela-log.ps1 e erros.ps1). A janela fecha sozinha num
# shutdown normal; se o servidor cair com erro, ela fica aberta para ler o motivo. Fechar no X derruba o servidor.
function Iniciar-Janela($cfg, [string] $titulo, [string] $comando, [string] $pasta, [string] $arquivoErros) {
    $env:JANELA_TITULO = $titulo
    $env:JANELA_COMANDO = $comando
    $env:JANELA_ERROS = Join-Path $cfg.Logs $arquivoErros
    $script = Join-Path $PSScriptRoot 'janela-log.ps1'
    Start-Process (Join-Path $PSHOME 'powershell.exe') -WorkingDirectory $pasta `
        -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$script`"" | Out-Null
}

# Fecha a janela de um servidor: os ancestrais cmd/powershell do processo, até a janela-log.ps1
function Fechar-Janela([int] $processoId) {
    $atual = Get-CimInstance Win32_Process -Filter "ProcessId=$processoId"
    while ($atual) {
        $pai = Get-CimInstance Win32_Process -Filter "ProcessId=$($atual.ParentProcessId)"
        if (-not $pai -or $pai.Name -notin 'cmd.exe', 'powershell.exe') { break }
        if ($pai.Name -eq 'powershell.exe' -and $pai.CommandLine -notlike '*janela-log.ps1*') { break }
        Stop-Process -Id $pai.ProcessId -Force -ErrorAction SilentlyContinue
        if ($pai.Name -eq 'powershell.exe') { break }
        $atual = $pai
    }
}

# Processo que escuta na porta (para parar o front sem guardar PID)
function Processo-Na-Porta([int] $porta) {
    $conexao = Get-NetTCPConnection -State Listen -LocalPort $porta -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conexao) { return Get-Process -Id $conexao.OwningProcess -ErrorAction SilentlyContinue }
}
