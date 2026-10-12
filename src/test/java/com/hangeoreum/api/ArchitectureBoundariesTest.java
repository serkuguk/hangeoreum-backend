package com.hangeoreum.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureBoundariesTest {
    // Temporary transport DTO debt: exact type edges only, never MVC controllers.
    private static final Set<String> DTO_EXCEPTIONS = Set.of(
            "vocabulary/application/VocabularyService.java -> vocabulary.api.UserWordDto",
            "vocabulary/application/VocabularyService.java -> vocabulary.api.WordDto",
            "vocabulary/application/ReviewService.java -> vocabulary.api.UserWordDto",
            "vocabulary/application/VocabularyAdministrationService.java -> vocabulary.api.WordDto",
            "identity/application/UserAdministrationService.java -> identity.api.UserDto",
            "learning/application/LessonBuilderService.java -> vocabulary.api.WordDto");

    @Test
    void controllersDelegateAndContextsOwnTheirPersistence() throws Exception {
        Path root = Path.of("src/main/java/com/hangeoreum/api");
        Pattern dependency = Pattern.compile("com\\.hangeoreum\\.api\\.([a-z]+)\\.(infrastructure|api)\\.([\\w*]+)");
        Set<String> violations = new HashSet<>();
        Set<String> usedExceptions = new HashSet<>();
        try (var sources = Files.walk(root)) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                String[] parts = relative.split("/");
                if (parts.length < 3) continue;
                String source = Files.readString(file);
                var references = dependency.matcher(source);
                while (references.find()) {
                    String targetContext = references.group(1), targetLayer = references.group(2);
                    String edge = relative + " -> " + targetContext + "." + targetLayer + "." + references.group(3);
                    if (targetLayer.equals("infrastructure") && (parts[1].equals("api") || !parts[0].equals(targetContext))) {
                        violations.add(edge);
                    }
                    if (parts[1].equals("application") && targetLayer.equals("api")) {
                        if (DTO_EXCEPTIONS.contains(edge)) usedExceptions.add(edge);
                        else violations.add(edge);
                    }
                }
                if (parts[1].equals("api") && source.contains("@Transactional")) {
                    violations.add(relative + " owns a transaction");
                }
            }
        }
        assertEquals(Set.of(), violations, "Architecture boundary violations");
        assertEquals(DTO_EXCEPTIONS, usedExceptions, "Remove resolved temporary exceptions");
    }
}
