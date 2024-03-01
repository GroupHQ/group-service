package org.grouphq.groupservice.config;

import java.util.ArrayList;
import java.util.List;
import org.grouphq.groupservice.config.converters.EventDataModelToJsonConverter;
import org.grouphq.groupservice.config.converters.JsonToEventDataModelConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
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

    @Bean
    public R2dbcCustomConversions customConversions() {
        final List<Converter<?, ?>> converters = new ArrayList<>();

        converters.add(new EventDataModelToJsonConverter());
        converters.add(new JsonToEventDataModelConverter());

        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }
}
