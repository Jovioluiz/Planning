<#
.SYNOPSIS
  Mostra os últimos erros do WildFly e do front (gravados pelas janelas do iniciar.ps1) e copia para a área de
  transferência, pronto para colar.
.PARAMETER Quantidade
  Quantos erros, contando do mais recente (padrão 5).
.PARAMETER Limpar
  Apaga os arquivos de erros (depois de corrigir, para começar do zero).
#>
param([int] $Quantidade = 5, [switch] $Limpar)
. "$PSScriptRoot\comum.ps1"
$cfg = Carregar-Config

$arquivos = 'erros-backend.log', 'erros-front.log' | ForEach-Object { Join-Path $cfg.Logs $_ }

if ($Limpar) {
    $arquivos | Where-Object { Test-Path $_ } | Remove-Item
    Write-Host 'Arquivos de erros apagados.'
    return
}

# Cada erro começa com "----- <janela>, <data hora> -----"
$blocos = New-Object System.Collections.Generic.List[object]
foreach ($arquivo in $arquivos | Where-Object { Test-Path $_ }) {
    $atual = $null
    foreach ($linha in Get-Content $arquivo -Encoding UTF8) {
        if ($linha -match '^----- .+, (\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) -----$') {
            $atual = [pscustomobject]@{ Quando = [datetime]$Matches[1]; Linhas = New-Object System.Collections.Generic.List[string] }
            $blocos.Add($atual)
        }
        if ($atual) { $atual.Linhas.Add($linha) }
    }
}

if ($blocos.Count -eq 0) {
    Write-Host 'Nenhum erro registrado.' -ForegroundColor Green
    return
}

$ultimos = $blocos | Sort-Object Quando | Select-Object -Last $Quantidade
$texto = ($ultimos | ForEach-Object { $_.Linhas -join "`r`n" }) -join "`r`n`r`n"
Write-Host $texto -ForegroundColor Red
Set-Clipboard -Value $texto
Write-Host ''
Write-Host "$(@($ultimos).Count) de $($blocos.Count) erro(s) copiados para a área de transferência (Ctrl+V para colar)." `
    -ForegroundColor Cyan
