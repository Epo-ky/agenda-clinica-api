package br.com.agendaclinica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * O "agora" vira uma dependência injetável. Nos testes trocamos por um relógio fixo,
 * senão as regras de antecedência dependeriam da hora em que o teste roda.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
