package org.grouphq.groupservice.config.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.UncheckedIOException;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.outbox.EventDataModel;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Converter to be registered with Spring Data JDBC to serialize EventDataModel objects.
 * This is probably not needed but since the ReadingConverter is, we're including this.
 *
 * @author makmn
 * @since 2/29/2024
 */
@WritingConverter
@Slf4j
public class EventDataModelToJsonConverter implements Converter<EventDataModel, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventDataModelToJsonConverter() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convert(@NotNull EventDataModel eventDataModel) {
        try {
            return objectMapper.writeValueAsString(eventDataModel);
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("Could not convert EventDataModel to string {}", eventDataModel, jsonProcessingException);
            throw new UncheckedIOException(jsonProcessingException);
        }
    }
}