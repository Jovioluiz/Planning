package com.planningapp.security;

import java.util.Set;

public record TokenValidado(String usuario, Set<String> papeis) {
}
