package com.planningapp.security;

import org.mindrot.jbcrypt.BCrypt;

import java.util.regex.Pattern;

/**
 * Senhas migradas do Planning original, que o Spring Security gravava em BCrypt ({@code $2a$10$...}). O
 * PBKDF2 do Jakarta Security não as entende, e hash não se converte em outro hash: a saída é conferir o BCrypt no
 * login e, se a senha bater, regravá-la em PBKDF2 (ver {@code AuthService.login}). Depois do primeiro login de
 * cada usuário, a senha dele não passa mais por aqui.
 */
public final class SenhaLegada {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private SenhaLegada() {
    }

    public static boolean ehBcrypt(String hash) {
        return hash != null && BCRYPT.matcher(hash).matches();
    }

    /**
     * Como o original: ele fazia {@code trim()} da senha antes de gerar e de conferir o hash. As variantes
     * {@code $2b$}/{@code $2y$} são o mesmo algoritmo; a jBCrypt só reconhece o prefixo {@code $2a$}.
     */
    public static boolean confere(String senha, String hash) {
        if (senha == null || !ehBcrypt(hash)) {
            return false;
        }
        return BCrypt.checkpw(senha.trim(), "$2a$" + hash.substring(4));
    }
}
