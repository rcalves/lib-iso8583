package com.raphati.lib.iso.utils;

import com.raphati.lib.iso.exception.IsoParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceUtilsTest {

    @Test
    void missingClasspathResourceThrows() {
        assertThatThrownBy(() -> ResourceUtils.open("classpath:does/not/exist.xml"))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("classpath:does/not/exist.xml");
    }

    @Test
    void readsFilesystemPath(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sample.txt");
        Files.writeString(file, "hello iso", StandardCharsets.UTF_8);

        try (InputStream in = ResourceUtils.open(file.toString())) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello iso");
        }
    }

    @Test
    void missingFilesystemPathThrows(@TempDir Path dir) {
        String path = dir.resolve("missing.json").toString();
        assertThatThrownBy(() -> ResourceUtils.open(path))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining(path);
    }

    @Test
    void blankPathThrows() {
        assertThatThrownBy(() -> ResourceUtils.open(" ")).isInstanceOf(IsoParserException.class);
    }
}
