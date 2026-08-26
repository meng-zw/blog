package com.blog.media.storage;

import com.blog.media.StorageProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Resolves the configured storage adapter for persisted media locations.
 */
@Component
/** 存储提供方注册表；业务只依赖此入口，从而将切换 Local/R2/Cloudreve 的影响限制在配置层。 */
public class ObjectStorageRegistry {
    private final Map<StorageProvider, ObjectStorage> storages;

    public ObjectStorageRegistry(Collection<ObjectStorage> storages) {
        Map<StorageProvider, ObjectStorage> configured = new EnumMap<>(StorageProvider.class);
        for (ObjectStorage storage : storages) {
            if (storage == null) {
                throw new IllegalArgumentException("Object storage adapter is required");
            }
            StorageProvider provider = storage.provider();
            if (provider == null) {
                throw new IllegalArgumentException("Object storage provider is required");
            }
            if (configured.putIfAbsent(provider, storage) != null) {
                throw new IllegalArgumentException("Multiple object storage adapters are configured for provider " + provider);
            }
        }
        this.storages = Map.copyOf(configured);
    }

    public ObjectStorage get(StorageProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Storage provider is required");
        }
        ObjectStorage storage = storages.get(provider);
        if (storage == null) {
            throw new IllegalArgumentException("No object storage adapter is configured for provider " + provider);
        }
        return storage;
    }
}
