package com.planningapp.service.email;

/** Texto do e-mail com o link de redefinição de senha (versão texto e HTML). */
public record EmailRedefinicaoSenha(String assunto, String texto, String html) {

    public static EmailRedefinicaoSenha montar(String usuario, String link, long minutosValidade, boolean producao) {
        String assunto = (producao ? "" : "[TESTES] ") + "Chutômetro: redefinição de senha";
        String texto = """
                Olá, %1$s!

                Recebemos um pedido para redefinir a sua senha no Chutômetro. Para escolher uma nova senha, abra o link:

                %2$s

                O link vale por %3$d minutos e só pode ser usado uma vez.
                Se não foi você que pediu, ignore este e-mail: a sua senha continua a mesma.
                """.formatted(usuario, link, minutosValidade);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:auto;color:#111">
                  <h2 style="margin:0 0 16px">Chutômetro</h2>
                  <p>Olá, <strong>%1$s</strong>!</p>
                  <p>Recebemos um pedido para redefinir a sua senha. Para escolher uma nova senha, clique no botão:</p>
                  <p style="margin:24px 0">
                    <a href="%2$s" style="background:#ff6a13;color:#fff;padding:12px 22px;border-radius:999px;text-decoration:none;font-weight:bold">Redefinir minha senha</a>
                  </p>
                  <p style="font-size:13px;color:#555">Ou copie este endereço no navegador:<br>
                    <a href="%2$s" style="color:#555;word-break:break-all">%2$s</a></p>
                  <p style="font-size:13px;color:#555">O link vale por %3$d minutos e só pode ser usado uma vez.
                    Se não foi você que pediu, ignore este e-mail: a sua senha continua a mesma.</p>
                </div>
                """.formatted(escapar(usuario), escapar(link), minutosValidade);
        return new EmailRedefinicaoSenha(assunto, texto, html);
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
