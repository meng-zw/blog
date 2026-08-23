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

    UploadTicket createDirectUpload(ObjectUploadRequest request);

    StoredObject upload(ObjectUploadRequest request, InputStream content) throws IOException;

    StoredObject inspect(String objectKey);

    InputStream openStream(String objectKey) throws IOException;

    URI resolvePublicUrl(String objectKey);

    void delete(String objectKey) throws IOException;
}
