package com.blog.media;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MediaAssetMappingTest {

    @Test
    void mapsProviderStatusAndPurposeAsStringEnumsWithLifecycleMetadata() throws Exception {
        assertThat(MediaAsset.class.getDeclaredFields())
                .extracting(Field::getName)
                .contains("provider", "bucket", "status", "purpose", "etag", "confirmedAt", "updatedAt",
                        "operationToken");

        assertStringEnum("provider", "com.blog.media.StorageProvider");
        assertStringEnum("status", "com.blog.media.MediaStatus");
        assertStringEnum("purpose", "com.blog.media.MediaPurpose");
        assertThat(field("bucket").getType()).isEqualTo(String.class);
        assertThat(field("bucket").getAnnotation(Column.class).nullable()).isFalse();
        assertThat(field("etag").getType()).isEqualTo(String.class);
        assertThat(field("confirmedAt").getType()).isEqualTo(Instant.class);
        assertThat(field("updatedAt").getType()).isEqualTo(Instant.class);
        assertThat(field("operationToken").getType()).isEqualTo(String.class);
    }

    @Test
    void leavesMediaLocationUniquenessToTheFlywayGeneratedHash() throws Exception {
        Table table = MediaAsset.class.getAnnotation(Table.class);

        assertThat(table.uniqueConstraints()).isEmpty();
        assertThat(field("storageKey").getAnnotation(Column.class).unique()).isFalse();
    }

    @Test
    void exposesAnOwnerScopedAssetLookup() throws Exception {
        Method lookup = MediaAssetRepository.class.getDeclaredMethod("findByIdAndUploadedById", Long.class, Long.class);

        assertThat(lookup.getReturnType().getName()).isEqualTo("java.util.Optional");
    }

    private static void assertStringEnum(String fieldName, String enumTypeName) throws Exception {
        Field field = field(fieldName);

        assertThat(field.getType().getName()).isEqualTo(enumTypeName);
        assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    }

    private static Field field(String name) throws NoSuchFieldException {
        return MediaAsset.class.getDeclaredField(name);
    }
}
