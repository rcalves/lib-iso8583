package com.raphati.lib.iso.utils;

import com.raphati.lib.iso.exception.IsoParserException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens resources by path. A {@code classpath:} prefix loads from the classpath; anything else is a filesystem path.
 */
public final class ResourceUtils {

    public static final String CLASSPATH_PREFIX = "classpath:";

    private ResourceUtils() {
    }

    /**
     * Opens the resource. The caller must close the returned stream.
     *
     * @throws IsoParserException if the resource is missing or cannot be read
     */
    public static InputStream open(String path) {
        if (path == null || path.isBlank()) {
            throw new IsoParserException("Resource path must not be blank: " + path);
        }
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return openClasspath(path);
        }
        try {
            return Files.newInputStream(Path.of(path));
        } catch (IOException | RuntimeException e) {
            throw new IsoParserException("Unable to open resource: " + path, e);
        }
    }

    private static InputStream openClasspath(String path) {
        String name = path.substring(CLASSPATH_PREFIX.length());
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        InputStream in = null;
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            in = context.getResourceAsStream(name);
        }
        if (in == null) {
            ClassLoader own = ResourceUtils.class.getClassLoader();
            if (own != null) {
                in = own.getResourceAsStream(name);
            }
        }
        if (in == null) {
            throw new IsoParserException("Resource not found: " + path);
        }
        return in;
    }
}
