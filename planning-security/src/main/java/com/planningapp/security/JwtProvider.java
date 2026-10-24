package com.planningapp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Gera e valida JWT assinados com HMAC-SHA256.
 *
 * <p>Configuração por variável de ambiente:</p>
 * <ul>
 *     <li>{@code JWT_SECRET}: segredo com pelo menos 32 caracteres (obrigatório em produção)</li>
 *     <li>{@code JWT_VALIDADE_MINUTOS}: validade do token, padrão 480 (8 horas)</li>
 * </ul>
 */
@ApplicationScoped
public class JwtProvider {

    static final String EMISSOR = "planning-jakarta";
    static final String CLAIM_PAPEIS = "groups";

    private static final Logger LOG = Logger.getLogger(JwtProvider.class.getName());
    private static final String SEGREDO_DEV = "planning-jakarta-dev-inseguro-troque-em-producao!";
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private byte[] segredo;
    private Duration validade;

    /** Usado pelo CDI; a configuração é lida em {@link #iniciar()}. */
    public JwtProvider() {
    }

    JwtProvider(String segredo, Duration validade) {
        configurar(segredo, validade);
    }

    @PostConstruct
    void iniciar() {
        String segredoEnv = System.getenv("JWT_SECRET");
        if (segredoEnv == null || segredoEnv.isBlank()) {
            LOG.warning("JWT_SECRET não definido: usando segredo de desenvolvimento. NÃO use assim em produção.");
            segredoEnv = SEGREDO_DEV;
        }
        long minutos = Long.parseLong(System.getenv().getOrDefault("JWT_VALIDADE_MINUTOS", "480"));
        configurar(segredoEnv, Duration.ofMinutes(minutos));
    }

    private void configurar(String segredo, Duration validade) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalArgumentException("O segredo do JWT deve ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " bytes");
        }
        this.segredo = bytes;
        this.validade = validade;
    }

    public TokenGerado gerar(String usuario, Set<String> papeis) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(validade);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(EMISSOR)
                .subject(usuario)
                .issueTime(Date.from(agora))
                .expirationTime(Date.from(expiraEm))
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_PAPEIS, List.copyOf(papeis))
                .build();

        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(segredo));
            return new TokenGerado(jwt.serialize(), expiraEm);
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o JWT", e);
        }
    }

    /** Retorna vazio para qualquer token malformado, com assinatura inválida, de outro emissor ou expirado. */
    public Optional<TokenValidado> validar(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // Fixar o algoritmo evita aceitar tokens com "alg" trocado pelo cliente
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(segredo))) {
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiracao = claims.getExpirationTime();
            if (!EMISSOR.equals(claims.getIssuer())
                    || claims.getSubject() == null
                    || expiracao == null
                    || expiracao.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }

            List<String> papeis = claims.getStringListClaim(CLAIM_PAPEIS);
            return Optional.of(new TokenValidado(claims.getSubject(), papeis == null ? Set.of() : new HashSet<>(papeis)));
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }
}
