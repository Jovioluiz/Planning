package com.planningapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Rota de login pública
                .requestMatchers("/api/auth/login").permitAll()
                // WebSocket público (autenticação via sessão do próprio STOMP se necessário)
                .requestMatchers("/ws-planning/**").permitAll()
                // Todas as demais requerem autenticação
                // NOTA: para um controle fino por perfil, adicione
                // @PreAuthorize("hasRole('ADMIN')") nos métodos dos controllers
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
