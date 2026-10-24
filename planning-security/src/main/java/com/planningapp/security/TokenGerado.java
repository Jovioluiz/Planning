package com.planningapp.security;

import java.time.Instant;

public record TokenGerado(String token, Instant expiraEm) {
}
