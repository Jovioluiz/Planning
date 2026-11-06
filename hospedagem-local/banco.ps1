<#
.SYNOPSIS
  Mostra ou troca o banco que o servidor usa: produção ou testes (os dois no mesmo PostgreSQL, nomes em Bancos no
  config.ps1). A escolha fica em <Servidor>\banco-ativo.txt e vale para o iniciar.ps1, o migrar.ps1 etc.

  Com o WildFly no ar, ele é reiniciado com o banco novo (o front não precisa: ele pergunta o ambiente à API e mostra
  a faixa "Ambiente de testes"). Atenção: o chutometro.dev.br aponta para este mesmo servidor, então enquanto
  estiver em testes, todo mundo que entrar usa o banco de testes.

  Exemplos:
    .\banco.ps1                          mostra qual está em uso
    .\banco.ps1 testes                   passa a usar o banco de testes (cria vazio se não existir)
    .\banco.ps1 testes -CopiarProducao   recria o banco de testes como cópia da produção e passa a usá-lo
    .\banco.ps1 producao                 volta para a produção
.PARAMETER Ambiente
  producao ou testes.
.PARAMETER CopiarProducao
  Apaga o banco de testes e o recria com uma cópia dos dados da produção (pg_dump + pg_restore). Só com "testes".
.PARAMETER SemReiniciar
  Só grava a escolha; o banco novo vale no próximo iniciar.ps1.
#>
param(
    [ValidateSet('producao', 'testes')] [string] $Ambiente,
    [switch] $CopiarProducao,
    [switch] $SemReiniciar
)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config

function Mostrar($cfg) {
    $cor = if ($cfg.Ambiente -eq 'producao') { 'Green' } else { 'Yellow' }
    Write-Host "Banco em uso: $($cfg.Ambiente) ($($cfg.Banco))" -ForegroundColor $cor
    foreach ($nome in 'producao', 'testes') {
        $banco = $cfg.Bancos[$nome]
        $existe = if (Banco-Existe $cfg $banco) { 'existe' } else { 'ainda não criado' }
        Write-Host ("  {0,-9} {1} ({2})" -f $nome, $banco, $existe)
    }
}

if (-not $Ambiente) {
    if ($CopiarProducao) { throw 'Use: .\banco.ps1 testes -CopiarProducao' }
    Mostrar $cfg
    Write-Host ''
    Write-Host 'Para trocar: .\banco.ps1 testes   ou   .\banco.ps1 producao'
    return
}
if ($CopiarProducao -and $Ambiente -ne 'testes') {
    throw '-CopiarProducao só vale para o banco de testes (a produção nunca é sobrescrita por aqui).'
}

$wildflyNoAr = Porta-Ocupada $cfg.PortaHttp
$banco = $cfg.Bancos[$Ambiente]
if ($banco -eq $cfg.BancoProducao -and $Ambiente -ne 'producao') {
    throw "Bancos.$Ambiente e Bancos.producao são o mesmo banco ($banco) no config.ps1."
}

# O WildFly para antes de mexer no banco que ele está usando
$precisaParar = $wildflyNoAr -and -not $SemReiniciar -and ($Ambiente -ne $cfg.Ambiente -or $CopiarProducao)
if ($CopiarProducao -and $wildflyNoAr -and $cfg.Ambiente -eq 'testes' -and $SemReiniciar) {
    throw 'O WildFly está usando o banco de testes: rode sem -SemReiniciar para ele parar durante a cópia.'
}
if ($precisaParar) {
    Etapa 'Parando o WildFly'
    & "$PSScriptRoot\parar.ps1" -SoBackend
}

if ($CopiarProducao) {
    Etapa "Copiando a produção ($($cfg.BancoProducao)) para o banco de testes ($banco)"
    New-Item -ItemType Directory -Force $cfg.Backups | Out-Null
    $copia = Join-Path $cfg.Backups ("copia-para-testes-{0:yyyyMMdd-HHmm}.dump" -f (Get-Date))
    $env:PGPASSWORD = $cfg.PgSenha
    $conexao = @('-h', $cfg.PgHost, '-p', "$($cfg.PgPorta)", '-U', $cfg.PgUsuario)
    Executar (Join-Path $cfg.PostgresBin 'pg_dump.exe') ($conexao + @('-Fc', '-f', $copia, $cfg.BancoProducao))
    # Derruba conexões que tenham sobrado (ex.: pgAdmin) antes de apagar
    Psql $cfg 'postgres' "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$banco' AND pid <> pg_backend_pid()" | Out-Null
    Psql $cfg 'postgres' "DROP DATABASE IF EXISTS $banco" | Out-Null
    Criar-Banco $cfg $banco | Out-Null
    Executar (Join-Path $cfg.PostgresBin 'pg_restore.exe') ($conexao + @('--no-owner', '-d', $banco, $copia))
    Remove-Item $copia
    Write-Host 'Cópia pronta. Os e-mails cadastrados também vieram: cuidado ao testar o "Esqueci minha senha".'
} elseif (Criar-Banco $cfg $banco) {
    Write-Host "Banco $banco criado vazio: as tabelas são criadas no deploy (Liquibase)."
    Write-Host 'O primeiro usuário cadastrado com perfil Moderador vira ADMIN; o SUPER só é criado se SuperSenha estiver no config.ps1.'
    Write-Host 'Para começar com os dados da produção: .\banco.ps1 testes -CopiarProducao'
}

Set-Content -Path $cfg.ArquivoBancoAtivo -Value $Ambiente -Encoding ASCII
$cfg = Carregar-Config
Write-Host ''
Mostrar $cfg

if ($SemReiniciar) {
    if ($wildflyNoAr) { Write-Host 'O WildFly continua no banco anterior até ser reiniciado (.\parar.ps1 e .\iniciar.ps1).' }
    return
}
if ($precisaParar) {
    Etapa 'Subindo o WildFly com o banco novo'
    & "$PSScriptRoot\iniciar.ps1" -SoBackend
} elseif ($wildflyNoAr) {
    Write-Host 'O WildFly já estava neste banco: nada a reiniciar.'
}
