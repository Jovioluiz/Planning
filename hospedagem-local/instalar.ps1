<#
.SYNOPSIS
  Prepara a máquina para rodar o planning-jakarta sem Docker: WildFly configurado, banco criado, EAR e front
  compilados. Pode rodar de novo sem problema (o que já está feito é pulado).

  Não inicia nada e não migra dados: depois dele, migrar.ps1 (uma vez) e iniciar.ps1.
#>
. "$PSScriptRoot\comum.ps1"

# --- 1. Configuração --------------------------------------------------------------------------------------------
$config = Arquivo-Config
if (-not (Test-Path $config)) {
    Etapa 'Criando config.ps1 a partir do config.exemplo.ps1 (com JWT_SECRET e REDMINE_SEGREDO aleatórios)'
    $gerador = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 48
    $gerador.GetBytes($bytes)
    $segredo = [Convert]::ToBase64String($bytes)
    $gerador.GetBytes($bytes)
    $segredoRedmine = [Convert]::ToBase64String($bytes)
    $texto = (Get-Content (Join-Path $PSScriptRoot 'config.exemplo.ps1') -Raw -Encoding UTF8) `
        -replace '__GERADO_NO_INSTALAR_REDMINE__', $segredoRedmine `
        -replace '__GERADO_NO_INSTALAR__', $segredo
    [IO.File]::WriteAllText($config, $texto, (New-Object Text.UTF8Encoding $true))
    Write-Host "Criado: $config"
    Write-Host 'Confira a senha do Postgres (PgSenha) nele antes de continuar, se não for "postgres".'
}
$cfg = Carregar-Config

# --- 2. Pré-requisitos -------------------------------------------------------------------------------------------
Etapa 'Verificando Java 21+, Maven, Node e PostgreSQL'
foreach ($cmd in 'java', 'mvn', 'node', 'npm.cmd') {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { throw "Não encontrei '$cmd' no PATH." }
}
$versaoJava = (Get-Command java).Version
if ($versaoJava.Major -lt 21) { throw "Precisa do Java 21 ou mais novo (o java do PATH é o $versaoJava)." }
if (-not (Test-Path (Join-Path $cfg.PostgresBin 'psql.exe'))) { throw "Não achei o psql.exe em $($cfg.PostgresBin)." }
$servicoPg = Get-Service 'postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($servicoPg -and $servicoPg.Status -ne 'Running') { throw "O serviço $($servicoPg.Name) está parado." }
Psql $cfg 'postgres' 'SELECT 1' | Out-Null
Write-Host "OK (Postgres em $($cfg.PgHost):$($cfg.PgPorta))"

New-Item -ItemType Directory -Force $cfg.Servidor, $cfg.Logs, $cfg.Backups | Out-Null

# --- 3. WildFly ---------------------------------------------------------------------------------------------------
if (-not (Test-Path $cfg.WildFly)) {
    Etapa "Baixando o WildFly $WildFlyVersao (pelo Maven, o mesmo zip dos testes de integração)"
    $tmp = Join-Path $cfg.Servidor 'tmp'
    New-Item -ItemType Directory -Force $tmp | Out-Null
    Executar 'mvn' @('-q', '-B', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy',
        "-Dartifact=org.wildfly:wildfly-dist:${WildFlyVersao}:zip", "-DoutputDirectory=$tmp")
    # tar (vem no Windows 10): bem mais rápido que o Expand-Archive com os milhares de arquivos do WildFly
    Executar "$env:SystemRoot\System32\tar.exe" @('-xf', (Join-Path $tmp "wildfly-dist-$WildFlyVersao.zip"),
        '-C', $cfg.Servidor)
}

$marca = Join-Path $cfg.WildFly '.planning-configurado'
if (-not (Test-Path $marca)) {
    Etapa 'Configurando o WildFly (driver do PostgreSQL, datasource, segurança, cache de presença)'
    $driver = Join-Path $cfg.Servidor "tmp\postgresql-$PostgresDriverVersao.jar"
    if (-not (Test-Path $driver)) {
        Executar 'mvn' @('-q', '-B', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy',
            "-Dartifact=org.postgresql:postgresql:${PostgresDriverVersao}:jar",
            "-DoutputDirectory=$(Join-Path $cfg.Servidor 'tmp')")
    }
    # O mesmo script do Dockerfile; muda só o caminho do driver e o banco padrão (o real vem de DB_URL ao subir)
    $cli = (Get-Content (Join-Path $Raiz 'docker\configurar-wildfly.cli') -Raw -Encoding UTF8) `
        -replace '/tmp/postgresql\.jar', ($driver -replace '\\', '/') `
        -replace 'jdbc:postgresql://db:5432/planningjakarta', (Url-Jdbc $cfg $cfg.Banco)
    $arquivoCli = Join-Path $cfg.Servidor 'tmp\configurar-wildfly.cli'
    [IO.File]::WriteAllText($arquivoCli, $cli, (New-Object Text.UTF8Encoding $false))
    Executar (Join-Path $cfg.WildFly 'bin\jboss-cli.bat') @("--file=$arquivoCli")
    Remove-Item -Recurse -Force (Join-Path $cfg.WildFly 'standalone\configuration\standalone_xml_history') `
        -ErrorAction SilentlyContinue
    New-Item -ItemType File $marca | Out-Null
}

# --- 4. Banco -----------------------------------------------------------------------------------------------------
Etapa "Banco $($cfg.Banco)"
if (Psql $cfg 'postgres' "SELECT 1 FROM pg_database WHERE datname = '$($cfg.Banco)'") {
    Write-Host 'Já existe.'
} else {
    Psql $cfg 'postgres' "CREATE DATABASE $($cfg.Banco) ENCODING 'UTF8' TEMPLATE template0" | Out-Null
    Write-Host 'Criado (vazio; o schema vem do Liquibase, na migração ou no primeiro deploy).'
}

# --- 5. Aplicação -------------------------------------------------------------------------------------------------
& "$PSScriptRoot\implantar.ps1"

Etapa 'Pronto'
Write-Host @"
Próximos passos:
  1. Migrar os dados do Planning original (uma vez):  .\migrar.ps1 -Simular   e depois   .\migrar.ps1
  2. Parar o Planning original e iniciar este:        .\iniciar.ps1
  3. No painel da Cloudflare, apontar api.chutometro.dev.br para http://127.0.0.1:$($cfg.PortaHttp)
  4. Iniciar sozinho ao entrar no Windows + backup diário: .\registrar-inicializacao.ps1
Detalhes: HOSPEDAGEM-LOCAL.md
"@
