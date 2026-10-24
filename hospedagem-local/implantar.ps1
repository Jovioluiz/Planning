<#
.SYNOPSIS
  Compila o backend (EAR) e o front e coloca os dois no servidor. Use depois de mudar o código ou de um git pull.

  Com o WildFly no ar, ele mesmo troca o EAR (deployment scanner, sem reiniciar); o front, se estiver no ar,
  é reiniciado no fim.
.PARAMETER ComTestes
  Roda os testes unitários no build do Maven (por padrão são pulados, para ser rápido).
.PARAMETER SoBackend
  Não compila o front.
#>
param([switch] $ComTestes, [switch] $SoBackend)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config

Etapa 'Compilando o backend (mvn package)'
$argumentos = @('-q', '-B', '-f', (Join-Path $Raiz 'pom.xml'), 'package')
if (-not $ComTestes) { $argumentos += '-DskipTests' }
Executar 'mvn' $argumentos

$ear = Join-Path $Raiz 'planning-ear\target\planning.ear'
$deployments = Join-Path $cfg.WildFly 'standalone\deployments'
Copy-Item $ear $deployments -Force
# O scanner recusa um EAR que falhou antes enquanto o .failed existir
Remove-Item (Join-Path $deployments 'planning.ear.failed') -ErrorAction SilentlyContinue
Write-Host "EAR copiado para $deployments"

if ($SoBackend) { return }

Etapa "Compilando o front (configuração $($cfg.FrontConfiguracao))"
$frontFonte = Join-Path $Raiz 'frontend'
Push-Location $frontFonte
try {
    if (-not (Test-Path 'node_modules')) { Executar 'npm.cmd' @('ci', '--no-audit', '--no-fund') }
    Executar 'npx.cmd' @('ng', 'build', '--configuration', $cfg.FrontConfiguracao)
} finally {
    Pop-Location
}

# Copia para fora do projeto: um build novo não derruba o front que está rodando no meio da compilação
$front = Processo-Na-Porta $cfg.FrontPorta
$rodando = $front -and $front.ProcessName -eq 'node'
if ($rodando) { & "$PSScriptRoot\parar.ps1" -SoFront }
if (Test-Path $cfg.Front) { Remove-Item -Recurse -Force $cfg.Front }
Copy-Item -Recurse (Join-Path $frontFonte 'dist\planning-poker') $cfg.Front
# O server.mjs importa o Express e o @angular/ssr de node_modules
Copy-Item (Join-Path $frontFonte 'package.json'), (Join-Path $frontFonte 'package-lock.json') $cfg.Front
Push-Location $cfg.Front
try {
    Executar 'npm.cmd' @('ci', '--omit=dev', '--no-audit', '--no-fund')
} finally {
    Pop-Location
}
Write-Host "Front copiado para $($cfg.Front)"
if ($rodando) { & "$PSScriptRoot\iniciar.ps1" -SoFront }
