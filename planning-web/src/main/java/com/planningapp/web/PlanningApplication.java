package com.planningapp.web;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Ativa o JAX-RS: todos os recursos ficam sob /planning/api (o /planning é o context-root definido no EAR). */
@ApplicationPath("/api")
public class PlanningApplication extends Application {
}
