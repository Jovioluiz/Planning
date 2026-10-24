<#
.SYNOPSIS
  Sobe o WildFly e o front em segundo plano (sem janela) e espera os dois responderem.
  Cada um numa janela do cmd ("Planning - WildFly (backend)" e "Planning - Front"), com o log ao vivo.
  O log do WildFly fica também em wildfly-*\standalone\log\server.log.

  Faz também um backup do banco se o último tiver mais de 20 h (para quem não registrou o backup diário).
.PARAMETER SoFront
  Sobe só o front (usado pelo implantar.ps1).
#>
param([switch] $SoFront)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config
Definir-Ambiente $cfg

function Esperar([string] $nome, [scriptblock] $pronto, [int] $segundos) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        if (& $pronto) { Write-Host "$nome no ar."; return }
        Start-Sleep -Seconds 2
    }
    throw "$nome não respondeu em $segundos s. Veja a janela dele."
}

function Responde([string] $url) {
    try {
        Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5 | Out-Null
        return $true
    } catch [System.Net.WebException] {
        # O 404 da verificação serve se vier da aplicação (JSON); o 404 do WildFly antes do deploy é HTML
        $resposta = $_.Exception.Response
        return ($null -ne $resposta) -and ($resposta.ContentType -like 'application/json*')
    }
}

# --- WildFly ------------------------------------------------------------------------------------------------------
if (-not $SoFront) {
    if (Porta-Ocupada $cfg.PortaHttp) {
        Write-Host "A porta $($cfg.PortaHttp) já está em uso: o WildFly (ou outra coisa) já está no ar."
    } else {
        $ultimo = Get-ChildItem $cfg.Backups -Filter '*.dump' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime | Select-Object -Last 1
        if (-not $ultimo -or $ultimo.LastWriteTime -lt (Get-Date).AddHours(-20)) {
            try { & "$PSScriptRoot\backup.ps1" } catch { Write-Warning "Backup falhou: $_" }
        }

        Etapa "Iniciando o WildFly (http://127.0.0.1:$($cfg.PortaHttp)/planning)"
        $bat = Join-Path $cfg.WildFly 'bin\standalone.bat'
        # standalone-full.xml: o perfil com JMS (o tópico dos eventos das salas).
        # NOPAUSE: o .bat não espera tecla no shutdown normal (com erro, o "|| pause" da janela segura).
        $env:NOPAUSE = 'true'
        $comando = "`"$bat`" -c standalone-full.xml -b $($cfg.Bind) -bmanagement 127.0.0.1 " +
            "-Djboss.socket.binding.port-offset=$($cfg.PortaOffset)"
        Iniciar-Janela $cfg 'Planning - WildFly (backend)' $comando $cfg.WildFly 'erros-backend.log'
        Esperar 'WildFly' { Responde "http://127.0.0.1:$($cfg.PortaHttp)/planning/api/salas/codigo/verificacao" } 180
    }
}

# --- Front --------------------------------------------------------------------------------------------------------
if (Porta-Ocupada $cfg.FrontPorta) {
    Write-Host "A porta $($cfg.FrontPorta) já está em uso (o front, ou o ng serve do Planning original)."
} else {
    Etapa "Iniciando o front (http://localhost:$($cfg.FrontPorta))"
    $env:PORT = "$($cfg.FrontPorta)"
    Iniciar-Janela $cfg 'Planning - Front' "node `"$(Join-Path $cfg.Front 'server\server.mjs')`"" $cfg.Front 'erros-front.log'
    Esperar 'Front' { Responde "http://127.0.0.1:$($cfg.FrontPorta)/" } 60
}
