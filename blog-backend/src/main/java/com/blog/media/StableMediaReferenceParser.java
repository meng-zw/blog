package com.blog.media;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.parser.Parser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts unique, exact stable media image URLs from rendered Markdown source. */
public class StableMediaReferenceParser {
    private static final Pattern STABLE_MEDIA_URL = Pattern.compile("^/api/media/assets/([1-9]\\d*)$");

    private final Parser parser = Parser.builder().build();

    public List<Long> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        parser.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                stableMediaId(image.getDestination()).ifPresent(ids::add);
                visitChildren(image);
            }
        });
        return List.copyOf(ids);
    }

    private static Optional<Long> stableMediaId(String destination) {
        if (destination == null) return Optional.empty();
        Matcher matcher = STABLE_MEDIA_URL.matcher(destination);
        if (!matcher.matches()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
