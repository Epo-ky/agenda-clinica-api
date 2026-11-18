package br.com.agendaclinica.config;

import br.com.agendaclinica.auth.JwtAuthenticationFilter;
import br.com.agendaclinica.auth.JwtService;
import br.com.agendaclinica.usuario.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Regra geral: tudo exige token, exceto login, health e a documentação (Swagger).
 * Quem pode o quê é definido com @PreAuthorize em cada controller (@EnableMethodSecurity).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtService jwtService,
                                    UsuarioRepository usuarioRepository,
                                    SecurityErrorHandlers errorHandlers) throws Exception {
        http
            // CSRF protege sessões em cookie; aqui não há cookie nem sessão (token no cabeçalho).
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errorHandlers)
                .accessDeniedHandler(errorHandlers))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioRepository),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
