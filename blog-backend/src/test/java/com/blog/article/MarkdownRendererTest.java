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
    void removesScriptsEventHandlersJavascriptUrlsAndIframes() {
        String html = renderer.render("<script>alert(1)</script>\n"
                + "<img src=\"https://example.com/a.png\" onerror=\"alert(2)\">\n"
                + "[bad](javascript:evil)\n"
                + "<iframe src=\"https://evil.example\"></iframe>");

        assertThat(html).doesNotContain("<script", "onerror", "javascript:", "<iframe", "evil.example");
    }

    @Test
    void removesUnsupportedFtpLinks() {
        assertThat(renderer.render("[legacy](ftp://example.com/file)"))
                .doesNotContain("ftp://")
                .contains("legacy");
    }
}
