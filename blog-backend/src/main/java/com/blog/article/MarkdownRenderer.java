package com.blog.article;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

@Component
public class MarkdownRenderer {
    private static final Safelist SAFE_HTML = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "pre")
            .addAttributes("code", "class")
            .removeProtocols("a", "href", "ftp")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public String render(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("Markdown content is required");
        }
        Node documentNode = parser.parse(markdown);
        String rendered = htmlRenderer.render(documentNode);
        String clean = Jsoup.clean(rendered, "", SAFE_HTML,
                new Document.OutputSettings().prettyPrint(false));
        Document parsed = Jsoup.parseBodyFragment(clean);
        parsed.outputSettings().prettyPrint(false);
        addHeadingIds(parsed);
        hardenLinks(parsed);
        return parsed.body().html();
    }

    private static void addHeadingIds(Document document) {
        Set<String> usedIds = new HashSet<>();
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6")) {
            String base = headingSlug(heading.text());
            String candidate = base;
            for (int suffix = 2; !usedIds.add(candidate); suffix++) {
                candidate = base + "-" + suffix;
            }
            heading.attr("id", candidate);
        }
    }

    private static void hardenLinks(Document document) {
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href").toLowerCase(Locale.ROOT);
            if (href.startsWith("http://") || href.startsWith("https://")) {
                link.attr("rel", "noopener noreferrer");
            }
        }
    }

    private static String headingSlug(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "section" : normalized;
    }
}
