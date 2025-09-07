package com.resilient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Minimal effort happy-path coverage: attempts to load every compiled class in the com.resilient package.
 * This gives a broad smoke signal that all classes are at least loadable under the test profile.
 */
class AllClassesSmokeTest {

    @Test
    void loadAllClasses() throws Exception {
        // Primary (Gradle) output directory
        Path root = Paths.get("build", "classes", "java", "main", "com", "resilient");
        if (Files.notExists(root)) {
            // Fallback (in case of alternative build layouts)
            return; // Nothing to do if classes not yet compiled.
        }

        List<String> failures = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> root.relativize(p))
                    .map(p -> p.toString().replace(File.separatorChar, '.').replaceAll("\\.class$", ""))
                    .map(rel -> "com.resilient." + rel)
                    .filter(name -> !name.contains("$")) // skip inner / synthetic classes for simplicity
                    .distinct()
                    .forEach(className -> {
                        try {
                            Class<?> cls = Class.forName(className);
                            Assertions.assertNotNull(cls, className + " should load");
                        } catch (Throwable t) {
                            failures.add(className + " -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
                        }
                    });
        }

        if (!failures.isEmpty()) {
            Assertions.fail("Failed to load classes (" + failures.size() + "): " + failures);
        }
    }
}
