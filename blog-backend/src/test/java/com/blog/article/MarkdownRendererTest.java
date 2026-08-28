package com.blog.article;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {
    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rendersHeadingsFencedJavaCodeAndLinksWithStableUniqueHeadingIds() {
        String markdown = "## Hello, World!\n\n## Hello, World!\n\n```java\nSystem.out.println(\"safe\");\n```\n\n[OpenAI](https://openai.com)";

        String first = renderer.render(markdown);
        String second = renderer.render(markdown);

        assertThat(first).isEqualTo(second)
                .contains("<h2 id=\"hello-world\">Hello, World!</h2>")
                .contains("<h2 id=\"hello-world-2\">Hello, World!</h2>")
                .contains("<code class=\"language-java\">")
                .contains("href=\"https://openai.com\"")
                .contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void headingIdsRemainGloballyUniqueWhenNaturalTextContainsGeneratedSuffix() {
        String html = renderer.render("## Foo\n\n## Foo\n\n## Foo-2");

        assertThat(html).contains("id=\"foo\"", "id=\"foo-2\"", "id=\"foo-2-2\"");
    }

    @Test
    void removesScriptsEventHandlersJavascriptUrlsAndIframes() {
        String html = renderer.render("<script>alert(1)</script>\n"
                + "<img src=\"https://example.com/a.png\" onerror=\"alert(2)\">\n"
                + "[bad](javascript:evil)\n"
                + "<iframe src=\"https://evil.example\"></iframe>");

        assertThat(html).doesNotContain("<script", "onerror", "href=\"javascript:", "<iframe", "evil.example");
    }

    @Test
    void sanitizerRejectsEncodedAndObfuscatedDangerousHrefAttributes() {
        String html = renderer.render("<a href=\"java&#x73;cript:alert(1)\">encoded</a>\n"
                + "<a href=\"JaVaScRiPt:evil\">mixed</a>");

        assertThat(html).contains("encoded", "mixed").doesNotContain("href=");
    }

    @Test
    void preservesJavascriptTextInOrdinaryProseAndFencedCode() {
        String html = renderer.render("The `javascript:` label is ordinary prose.\n\n```java\nString scheme = \"javascript:\";\n```");

        assertThat(html).contains("javascript:", "String scheme = \"javascript:\";");
    }

    @Test
    void removesUnsupportedFtpLinks() {
        assertThat(renderer.render("[legacy](ftp://example.com/file)"))
                .doesNotContain("ftp://")
                .contains("legacy");
    }

    @Test
    void preservesStableSameOriginMediaUrlsForPublicArticleRendering() {
        String html = renderer.render("![已上传图片](/api/media/assets/42)");

        assertThat(html).contains("<img src=\"/api/media/assets/42\" alt=\"已上传图片\">");
    }
}
