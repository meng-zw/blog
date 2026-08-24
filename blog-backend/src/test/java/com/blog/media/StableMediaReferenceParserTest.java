package com.blog.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StableMediaReferenceParserTest {

    @Test
    void extractsUniqueExactStableImageUrlsAndIgnoresExamplesAndForeignUrls() {
        StableMediaReferenceParser parser = new StableMediaReferenceParser();

        assertThat(parser.parse("""
                ![first](/api/media/assets/42)
                ![duplicate](/api/media/assets/42)
                ![with-query](/api/media/assets/43?cache=1)
                ![with-fragment](/api/media/assets/44#preview)
                ![foreign](https://example.test/api/media/assets/45)
                [ordinary link](/api/media/assets/46)
                ```md
                ![example](/api/media/assets/47)
                ```
                ![second](/api/media/assets/48)
                """))
                .containsExactly(42L, 48L);
    }
}
