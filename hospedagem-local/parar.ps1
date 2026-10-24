<#
.SYNOPSIS
  Para o front e o WildFly (o WildFly com shutdown normal, pela CLI de gerência).
.PARAMETER SoFront
  Para só o front (usado pelo implantar.ps1).
#>
param([switch] $SoFront)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config

$front = Processo-Na-Porta $cfg.FrontPorta
if ($front -and $front.ProcessName -eq 'node') {
    # Fecha a janela "Planning - Front" antes: com o node morto, ela ficaria esperando o Enter do erro
    Fechar-Janela $front.Id
    Stop-Process -Id $front.Id -Force -ErrorAction SilentlyContinue
    Write-Host 'Front parado.'
} elseif ($front) {
    Write-Warning "Na porta $($cfg.FrontPorta) está o processo '$($front.ProcessName)', não o front: não mexi nele."
}
if ($SoFront) { return }

if (Porta-Ocupada $cfg.PortaGerencia) {
    & (Join-Path $cfg.WildFly 'bin\jboss-cli.bat') --connect "--controller=127.0.0.1:$($cfg.PortaGerencia)" `
        --command=:shutdown | Out-Null
    $limite = (Get-Date).AddSeconds(60)
    while ((Porta-Ocupada $cfg.PortaHttp) -and (Get-Date) -lt $limite) { Start-Sleep -Seconds 1 }
    Write-Host 'WildFly parado.'
} else {
    Write-Host 'O WildFly não estava no ar.'
}
