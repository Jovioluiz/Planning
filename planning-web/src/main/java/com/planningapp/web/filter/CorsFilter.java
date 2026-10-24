package com.planningapp.web.filter;

import com.planningapp.web.config.OrigensPermitidas;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/** CORS para o front Angular. As origens aceitas vêm de {@link OrigensPermitidas}. */
@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String origem = request.getHeaderString("Origin");
        if (!OrigensPermitidas.permitida(origem)) {
            return;
        }
        MultivaluedMap<String, Object> headers = response.getHeaders();
        headers.putSingle("Access-Control-Allow-Origin", origem);
        headers.putSingle("Access-Control-Allow-Credentials", "true");
        headers.putSingle("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization");
        headers.putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Vary", "Origin");
    }
}
