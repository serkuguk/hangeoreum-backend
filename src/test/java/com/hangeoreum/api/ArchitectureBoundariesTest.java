package com.hangeoreum.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureBoundariesTest {
    @Test
    void controllersDelegateAndContextsOwnTheirPersistence() throws Exception {
        Path root = Path.of("src/main/java/com/hangeoreum/api");
        Pattern dependency = Pattern.compile("com\\.hangeoreum\\.api\\.([a-z]+)\\.(infrastructure|api|application|web)\\.([\\w*]+)");
        Set<String> violations = new HashSet<>();
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
                    if (parts[1].equals("domain")) {
                        violations.add(edge);
                    }
                    if (parts[1].equals("application") && targetLayer.equals("api")) {
                        violations.add(edge);
                    }
                }
                if (parts[1].equals("domain") && Pattern.compile("org\\.springframework\\.(web|http|data)\\.|jakarta\\.servlet\\.").matcher(source).find()) {
                    violations.add(relative + " depends on a web or Spring Data type");
                }
                if (parts[1].equals("api") && source.contains("@Transactional")) {
                    violations.add(relative + " owns a transaction");
                }
            }
        }
        assertEquals(Set.of(), violations, "Architecture boundary violations");
    }
}
