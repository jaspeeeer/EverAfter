package com.wedding.planner.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes attachment bytes under a configured root directory. Keys are always
 * {@code <projectId>/<attachmentId>} (assigned by {@code AttachmentService}, never user input),
 * so there is no path-traversal surface from a malicious filename.
 */
@Component
public class FilesystemAttachmentStorage implements AttachmentStorage {

    private final Path root;

    public FilesystemAttachmentStorage(
            @Value("${app.attachments.storage-root}") String storageRoot) throws IOException {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public long write(String key, InputStream data) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        return Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream read(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /** Resolves {@code key} under the storage root, rejecting any attempt to escape it. */
    private Path resolve(String key) {
        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Invalid attachment key: " + key);
        }
        return candidate;
    }
}
