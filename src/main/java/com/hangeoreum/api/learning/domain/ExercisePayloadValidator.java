package com.hangeoreum.api.learning.domain;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * ponytail: required-keys check per exercise kind instead of full JSON-schema;
 * upgrade to json-schema validation if content quality becomes a problem.
 */
public final class ExercisePayloadValidator {

    private static final Map<ExerciseKind, List<String>> REQUIRED = Map.of(
            ExerciseKind.CHOICE, List.of("question", "options"),
            ExerciseKind.LISTEN_CHOICE, List.of("audioUrl", "options"),
            ExerciseKind.WORD_ORDER, List.of("translation", "tokens"),
            ExerciseKind.FILL_BLANK, List.of("sentence", "options", "correct"),
            ExerciseKind.MATCH_PAIRS, List.of("pairs"),
            ExerciseKind.TYPE_WORD, List.of("translation", "answer"));

    private ExercisePayloadValidator() {
    }

    /** @return null when valid, otherwise the name of the first missing field */
    public static String validate(ExerciseKind kind, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return "payload";
        }
        for (String field : REQUIRED.get(kind)) {
            if (!payload.hasNonNull(field)) {
                return field;
            }
        }
        return null;
    }
}
