package com.blog.media.storage;

import com.blog.media.StorageProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Provider-neutral object operations used by the media application service.
 */
public interface ObjectStorage {
    StorageProvider provider();

    StorageCapabilities capabilities();

    /** Creates the complete persisted location for a newly generated server-owned key. */
    ObjectLocation locationForNewObject(String objectKey);

    UploadTicket createDirectUpload(ObjectLocation location, ObjectUploadRequest request);

    StoredObject upload(ObjectLocation location, ObjectUploadRequest request, InputStream content) throws IOException;

    StoredObject inspect(ObjectLocation location);

    InputStream openStream(ObjectLocation location) throws IOException;

    URI resolvePublicUrl(ObjectLocation location);

    void delete(ObjectLocation location) throws IOException;
}
