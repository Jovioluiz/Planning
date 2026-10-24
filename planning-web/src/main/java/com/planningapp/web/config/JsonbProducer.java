package com.planningapp.web.config;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/** Uma única instância de {@link Jsonb} (é thread-safe e cara de criar) para quem serializa fora do JAX-RS. */
@ApplicationScoped
public class JsonbProducer {

    private final Jsonb jsonb = JsonbBuilder.create();

    @Produces
    public Jsonb jsonb() {
        return jsonb;
    }

    @PreDestroy
    void fechar() throws Exception {
        jsonb.close();
    }
}
