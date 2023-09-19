package org.grouphq.groupservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import reactor.core.publisher.Mono;

/**
 * Configuration for Spring Data.
 */
@Configuration
@EnableR2dbcAuditing
public class DataConfig {


    @Bean
    ReactiveAuditorAware<String> auditorAware() {
        return () -> Mono.just("system");
    }
}
