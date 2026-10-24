<#
.SYNOPSIS
  Backup do banco com pg_dump (formato custom, comprimido) em <Servidor>\backups; apaga os mais velhos que
  BackupDias. Pode rodar com a aplicação no ar.

  Para restaurar (com o WildFly parado):
    pg_restore -h localhost -p 5433 -U postgres -d planningjakarta --clean --if-exists <arquivo.dump>
#>
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config
New-Item -ItemType Directory -Force $cfg.Backups | Out-Null

$arquivo = Join-Path $cfg.Backups ("$($cfg.Banco)-{0:yyyyMMdd-HHmm}.dump" -f (Get-Date))
$env:PGPASSWORD = $cfg.PgSenha
Executar (Join-Path $cfg.PostgresBin 'pg_dump.exe') @('-h', $cfg.PgHost, '-p', "$($cfg.PgPorta)", '-U', $cfg.PgUsuario,
    '-Fc', '-f', $arquivo, $cfg.Banco)
Write-Host "Backup: $arquivo"

Get-ChildItem $cfg.Backups -Filter "$($cfg.Banco)-*.dump" |
    Where-Object LastWriteTime -lt (Get-Date).AddDays(-$cfg.BackupDias) |
    Remove-Item
