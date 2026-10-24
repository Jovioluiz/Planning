<#
.SYNOPSIS
  Copia os dados do Planning original (banco BancoOriginal) para o planning-jakarta (banco Banco), no mesmo
  PostgreSQL. A origem só é lida. O destino precisa estar vazio: rode antes de iniciar o WildFly pela primeira vez.
.PARAMETER Simular
  Faz a migração inteira e desfaz no fim: só mostra o relatório. Rode sempre antes da migração de verdade.
.PARAMETER ModeradorLegado
  Quem modera a sala das tarefas sem sala (padrão: o primeiro ADMIN por id).
#>
param([switch] $Simular, [string] $ModeradorLegado)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config

if (-not $Simular -and (Porta-Ocupada $cfg.PortaHttp)) {
    throw "O WildFly está no ar (porta $($cfg.PortaHttp)). Pare com .\parar.ps1 antes de migrar de verdade."
}

$jar = Join-Path $Raiz 'planning-migracao\target\planning-migracao.jar'
if (-not (Test-Path $jar)) {
    Etapa 'Compilando a ferramenta de migração'
    Executar 'mvn' @('-q', '-B', '-f', (Join-Path $Raiz 'pom.xml'), '-pl', 'planning-migracao', '-am', 'package',
        '-DskipTests')
}

$env:ORIGEM_URL      = Url-Jdbc $cfg $cfg.BancoOriginal
$env:ORIGEM_USUARIO  = $cfg.PgUsuario
$env:ORIGEM_SENHA    = $cfg.PgSenha
$env:DESTINO_URL     = Url-Jdbc $cfg $cfg.Banco
$env:DESTINO_USUARIO = $cfg.PgUsuario
$env:DESTINO_SENHA   = $cfg.PgSenha

$argumentos = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-jar', $jar)
if ($Simular) { $argumentos += '--simular' }
if ($ModeradorLegado) { $argumentos += "--moderador-legado=$ModeradorLegado" }

Etapa "Migrando $($cfg.BancoOriginal) -> $($cfg.Banco)$(if ($Simular) { ' (simulação)' })"
if (-not $Simular) { & "$PSScriptRoot\backup.ps1" }
Executar 'java' $argumentos
