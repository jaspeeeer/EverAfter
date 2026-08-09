package com.wedding.planner.storage;

import java.io.InputStream;

/**
 * Abstraction over where attachment bytes physically live. {@link FilesystemAttachmentStorage} is
 * the only MVP implementation; a future cloud-backed implementation (Supabase Storage, S3) can
 * drop in behind this interface without a schema change — {@code attachments.storage_key} is
 * already opaque to callers.
 */
public interface AttachmentStorage {

    /** Persists the stream under {@code key} and returns the number of bytes written. */
    long write(String key, InputStream data) throws java.io.IOException;

    /** Opens the stored bytes for {@code key}. Caller must close the stream. */
    InputStream read(String key) throws java.io.IOException;

    /** Deletes the object at {@code key}. A no-op if it doesn't exist. */
    void delete(String key) throws java.io.IOException;
}
