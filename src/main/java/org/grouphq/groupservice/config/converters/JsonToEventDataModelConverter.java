package org.grouphq.groupservice.config.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.r2dbc.postgresql.codec.Json;
import java.io.UncheckedIOException;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.outbox.EventDataModel;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Converter to be registered with Spring Data JDBC to understand how to convert JSON column types from
 * PostgreSQL into the appropriate object. This is needed because the R2DBC driver
 * used passes to Spring Data JDBC a Json object, but Spring Data R2DBC currently
 * does not know how to serialize JSON types to their appropriate object types.
 *
 * @author makmn
 * @since 2/29/2024
 */
@ReadingConverter
@Slf4j
public class JsonToEventDataModelConverter implements Converter<Json, EventDataModel> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonToEventDataModelConverter() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public EventDataModel convert(@NotNull Json source) {
        try {
            return objectMapper.readValue(source.asString(), EventDataModel.class);
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("Could not convert source string to EventDataModel {}", source, jsonProcessingException);
            throw new UncheckedIOException(jsonProcessingException);
        }
    }
}
