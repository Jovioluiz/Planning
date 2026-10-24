package com.planningapp.web.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Origens do front aceitas pelo CORS (REST) e pelo handshake do WebSocket. Vêm de ALLOWED_ORIGINS, separadas por vírgula. */
public final class OrigensPermitidas {

    private static final Set<String> ORIGENS = Arrays.stream(
                    System.getenv().getOrDefault("ALLOWED_ORIGINS", "http://localhost:4200").split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

    private OrigensPermitidas() {
    }

    public static boolean permitida(String origem) {
        return origem != null && ORIGENS.contains(origem);
    }
}
