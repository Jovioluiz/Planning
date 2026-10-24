<#
.SYNOPSIS
  Janela de um servidor (aberta pelo iniciar.ps1): roda o comando e mostra o log ao vivo, com os erros em vermelho
  e os avisos em amarelo. Cada erro (com o stack trace inteiro) e cada aviso da aplicação (com.planningapp) também
  vão para o ArquivoErros, que o erros.ps1 lê.
#>
# Os parâmetros chegam por variável de ambiente (Iniciar-Janela, no comum.ps1): o comando tem aspas, e passá-lo
# pela linha de comando do powershell.exe exigiria escapar tudo duas vezes.
$Titulo = $env:JANELA_TITULO
$Comando = $env:JANELA_COMANDO
$ArquivoErros = $env:JANELA_ERROS
$Host.UI.RawUI.WindowTitle = $Titulo
# O java (-Dstdout.encoding=UTF-8) e o node escrevem em UTF-8
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# Linha do log do WildFly: "21:30:00,123 ERROR [categoria] (thread) mensagem"
$linhaWildFly = '^\d{2}:\d{2}:\d{2},\d{3}\s+(\w+)\s'
$emErro = $false
$suprimindo = $false
$bloco = $null
$continuacao = '^\s|^(Caused by|\w+(\.\w+)+(Exception|Error))'

function Gravar-Bloco {
    if ($script:bloco) {
        $cabecalho = "----- $Titulo, $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') -----"
        Add-Content -Path $ArquivoErros -Value (@($cabecalho) + $script:bloco) -Encoding UTF8
        $script:bloco = $null
    }
}

& cmd.exe /c "$Comando 2>&1" | ForEach-Object {
    $linha = "$_"
    $cor = $null
    if ($suprimindo -and $linha -match $continuacao) {
        return   # stack trace do acesso negado (ver abaixo)
    }
    $suprimindo = $false
    if ($linha -match $linhaWildFly) {
        # Nova entrada do WildFly: o nível decide; as linhas seguintes sem horário (stack trace) vão junto
        Gravar-Bloco
        $nivel = $Matches[1]
        # Acesso negado (401/403 de um @RolesAllowed) é normal, mas o WildFly o registra como ERROR com stack trace
        # (WFLYEJB0034 ... EJBAccessException). A linha amarela da requisição, logo depois, já diz quem tentou o quê.
        if ($nivel -eq 'ERROR' -and $linha -match 'WFLYEJB0034' -and $linha -match 'EJBAccessException') {
            $emErro = $false
            $suprimindo = $true
            $resumo = ($linha -replace ' WFLYEJB0034: .*? for method (?:public )?\S+ (\S+?)\(.*$', ' acesso negado em $1') `
                -replace ' ERROR ', ' NEGADO '
            Write-Host $resumo -ForegroundColor DarkGray
            return
        }
        # O WildFly usa ERROR/WARN; os loggers da aplicação (java.util.logging) saem como SEVERE/WARNING
        $emErro = $nivel -in 'ERROR', 'FATAL', 'SEVERE'
        if ($nivel -in 'WARN', 'WARNING') {
            $cor = 'Yellow'
            # Os avisos da aplicação (ex.: requisição recusada, com a mensagem que o front recebeu) vão para o arquivo
            if ($linha -match '\[com\.planningapp') {
                $bloco = New-Object System.Collections.Generic.List[string]
                $bloco.Add($linha)
            }
        }
    } elseif ($emErro -and $linha -match $continuacao) {
        # continuação do erro (stack trace)
    } elseif ($linha -match '(?i)\b(error|exception|fatal)\b') {
        # Erro fora do formato do WildFly (o node, ou o java antes do log subir)
        Gravar-Bloco
        $emErro = $true
    } else {
        Gravar-Bloco
        $emErro = $false
    }

    if ($emErro) {
        $cor = 'Red'
        if ($null -eq $bloco) { $bloco = New-Object System.Collections.Generic.List[string] }
        $bloco.Add($linha)
    }
    if ($cor) { Write-Host $linha -ForegroundColor $cor } else { Write-Host $linha }
}
Gravar-Bloco

$codigo = $LASTEXITCODE
if ($codigo -ne 0) {
    Write-Host ''
    Write-Host "$Titulo terminou com erro (código $codigo). Para copiar os erros: .\erros.ps1" -ForegroundColor Red
    Read-Host 'Enter para fechar'
}
