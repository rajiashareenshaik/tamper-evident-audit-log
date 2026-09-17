package com.audit.log.integrity;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TreeMap;

/**
 * Produces a deterministic JSON representation of an audit event's content, so that hashing the
 * same logical event always yields the same {@code contentHash} regardless of map key ordering.
 */
@Component
public class CanonicalJsonService {

    private final JsonMapper jsonMapper;

    public CanonicalJsonService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Serializes the given content to JSON with all map keys (including nested ones) sorted, so
     * the output is stable and safe to feed into {@link com.audit.log.integrity.HashService}.
     *
     * @param content the event content to canonicalize
     * @return a deterministic JSON string for {@code content}
     * @throws IllegalStateException if serialization fails
     */
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

    /**
     * @return a copy of {@code input} with keys sorted and all nested values normalized
     */
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

    /**
     * Recursively sorts map keys and normalizes elements inside nested maps/iterables; any other
     * value is returned unchanged.
     */
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