package com.wedding.planner.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round-trip and path-safety tests for the filesystem-backed attachment store. */
class FilesystemAttachmentStorageTest {

    @TempDir
    java.nio.file.Path tempDir;

    private FilesystemAttachmentStorage storage() throws IOException {
        return new FilesystemAttachmentStorage(tempDir.toString());
    }

    @Test
    void writeThenReadRoundTripsTheExactBytes() throws IOException {
        FilesystemAttachmentStorage storage = storage();
        byte[] content = "hello attachment".getBytes();

        long written = storage.write("proj-1/att-1", new ByteArrayInputStream(content));
        assertThat(written).isEqualTo(content.length);

        try (InputStream in = storage.read("proj-1/att-1")) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void deleteRemovesTheFileAndIsANoOpIfMissing() throws IOException {
        FilesystemAttachmentStorage storage = storage();
        storage.write("proj-1/att-2", new ByteArrayInputStream("x".getBytes()));

        storage.delete("proj-1/att-2");
        assertThatThrownBy(() -> storage.read("proj-1/att-2")).isInstanceOf(IOException.class);

        // Deleting again (already gone) must not throw.
        storage.delete("proj-1/att-2");
    }

    @Test
    void rejectsKeysThatEscapeTheStorageRoot() throws IOException {
        FilesystemAttachmentStorage storage = storage();
        assertThatThrownBy(() -> storage.write("../../etc/passwd", new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorCreatesTheRootDirectoryIfMissing() throws IOException {
        java.nio.file.Path nested = tempDir.resolve("does/not/exist/yet");
        new FilesystemAttachmentStorage(nested.toString());
        assertThat(Files.isDirectory(nested)).isTrue();
    }
}
