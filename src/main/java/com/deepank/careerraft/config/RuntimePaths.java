package com.deepank.careerraft.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimePaths {
    private RuntimePaths() {}

    public static Path root() {
        String configured = System.getenv("CAREER_RAFT_ROOT");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("data"))
                && Files.isDirectory(current.resolve("config"))
                && Files.isDirectory(current.resolve("resume"))) {
            return current;
        }

        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("data"))
                && Files.isDirectory(parent.resolve("config"))
                && Files.isDirectory(parent.resolve("resume"))) {
            return parent;
        }

        return current;
    }

    public static Path data() {
        return root().resolve("data");
    }

    public static Path config() {
        return root().resolve("config");
    }

    public static Path resume() {
        return root().resolve("resume");
    }
}
