<#
.SYNOPSIS
  Faz o planning-jakarta subir sozinho quando você entra no Windows (atalho na pasta Inicializar, sem precisar
  de administrador) e agenda um backup diário do banco (Agendador de Tarefas).
.PARAMETER Remover
  Desfaz os dois.
#>
param([switch] $Remover)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config

$atalho = Join-Path ([Environment]::GetFolderPath('Startup')) 'Planning Jakarta.lnk'
$tarefa = 'Planning Jakarta - backup diario'

if ($Remover) {
    Remove-Item $atalho -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $tarefa -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host 'Inicialização automática e backup diário removidos.'
    return
}

$powershell = Join-Path $PSHOME 'powershell.exe'

$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut($atalho)
$lnk.TargetPath = $powershell
$lnk.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$PSScriptRoot\iniciar.ps1`""
$lnk.WorkingDirectory = $PSScriptRoot
$lnk.WindowStyle = 7   # minimizado
$lnk.Save()
Write-Host "Inicia ao entrar no Windows: $atalho"

# Às 12h; se a máquina estiver desligada nesse horário, roda assim que der (StartWhenAvailable)
$acao = New-ScheduledTaskAction -Execute $powershell `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$PSScriptRoot\backup.ps1`""
$gatilho = New-ScheduledTaskTrigger -Daily -At 12:00
$ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
try {
    Register-ScheduledTask -TaskName $tarefa -Action $acao -Trigger $gatilho -Settings $ajustes -Force | Out-Null
    Write-Host "Backup diário agendado: '$tarefa' (backups em $($cfg.Backups), $($cfg.BackupDias) dias)"
} catch {
    Write-Warning "Não consegui agendar o backup ($($_.Exception.Message)). Rode este script num PowerShell como administrador, ou deixe só o backup que o iniciar.ps1 faz a cada início."
}
