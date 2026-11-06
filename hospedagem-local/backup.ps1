<#
.SYNOPSIS
  Backup do banco com pg_dump (formato custom, comprimido) em <Servidor>\backups; apaga os mais velhos que
  BackupDias. Pode rodar com a aplicação no ar.

  Por padrão faz o backup do banco de PRODUÇÃO, mesmo com o servidor usando o de testes (banco.ps1).

  Para restaurar (com o WildFly parado):
    pg_restore -h localhost -p 5433 -U postgres -d planningjakarta --clean --if-exists <arquivo.dump>
.PARAMETER Testes
  Faz o backup do banco de testes em vez do de produção.
#>
param([switch] $Testes)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config
New-Item -ItemType Directory -Force $cfg.Backups | Out-Null

$banco = if ($Testes) { $cfg.Bancos['testes'] } else { $cfg.BancoProducao }
$arquivo = Join-Path $cfg.Backups ("$banco-{0:yyyyMMdd-HHmm}.dump" -f (Get-Date))
$env:PGPASSWORD = $cfg.PgSenha
Executar (Join-Path $cfg.PostgresBin 'pg_dump.exe') @('-h', $cfg.PgHost, '-p', "$($cfg.PgPorta)", '-U', $cfg.PgUsuario,
    '-Fc', '-f', $arquivo, $banco)
Write-Host "Backup: $arquivo"

Get-ChildItem $cfg.Backups -Filter "$banco-*.dump" |
    Where-Object LastWriteTime -lt (Get-Date).AddDays(-$cfg.BackupDias) |
    Remove-Item
