package com.audit.log.integrity;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TreeMap;

@Component
public class CanonicalJsonService {

    private final JsonMapper jsonMapper;

    public CanonicalJsonService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String canonicalize(
            CanonicalAuditContent content
    ) {
        try {
            CanonicalAuditContent normalized =
                    new CanonicalAuditContent(
                            content.eventId(),
                            content.eventType(),
                            content.actorId(),
                            content.resourceType(),
                            content.resourceId(),
                            normalizeMap(content.payload()),
                            content.eventTimestamp(),
                            content.schemaVersion()
                    );

            return jsonMapper.writeValueAsString(normalized);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to canonicalize audit event",
                    e
            );
        }
    }

    private Map<String, Object> normalizeMap(
            Map<String, Object> input
    ) {
        Map<String, Object> sorted = new TreeMap<>();

        input.forEach(
                (key, value) -> sorted.put(
                        key,
                        normalizeValue(value)
                )
        );

        return sorted;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(Object value) {

        if (value instanceof Map<?, ?> map) {

            Map<String, Object> converted =
                    new TreeMap<>();

            map.forEach(
                    (key, nestedValue) ->
                            converted.put(
                                    String.valueOf(key),
                                    normalizeValue(nestedValue)
                            )
            );

            return converted;
        }

        if (value instanceof Iterable<?> iterable) {

            return java.util.stream.StreamSupport
                    .stream(
                            iterable.spliterator(),
                            false
                    )
                    .map(this::normalizeValue)
                    .toList();
        }

        return value;
    }
}