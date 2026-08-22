package com.blog.site;

import com.blog.article.ArticleRepository;
import com.blog.tool.ToolRepository;
import com.blog.topic.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SitemapService {
    private static final int BATCH_SIZE = 500;
    private static final String SITEMAP_NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";

    private final ArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final ToolRepository toolRepository;
    private final Clock clock;
    private final String baseUrl;

    @Autowired
    public SitemapService(ArticleRepository articleRepository, TopicRepository topicRepository,
                          ToolRepository toolRepository, @Value("${blog.public-base-url}") String baseUrl) {
        this(articleRepository, topicRepository, toolRepository, Clock.systemUTC(), baseUrl);
    }

    SitemapService(ArticleRepository articleRepository, TopicRepository topicRepository,
                   ToolRepository toolRepository, Clock clock, String baseUrl) {
        this.articleRepository = articleRepository;
        this.topicRepository = topicRepository;
        this.toolRepository = toolRepository;
        this.clock = clock;
        this.baseUrl = validateOrigin(baseUrl);
    }

    public String generate() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            xml.writeStartElement("urlset");
            xml.writeDefaultNamespace(SITEMAP_NAMESPACE);
            for (String path : List.of("/", "/articles", "/notes", "/topics", "/tools")) {
                writeUrl(xml, path);
            }
            Instant now = clock.instant();
            appendArticles(xml, now);
            appendTopics(xml);
            appendTools(xml, now);
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toString(StandardCharsets.UTF_8);
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Unable to generate sitemap", exception);
        }
    }

    private void appendArticles(XMLStreamWriter xml, Instant now) throws XMLStreamException {
        long afterId = 0;
        while (true) {
            List<ArticleRepository.SitemapRow> batch = articleRepository
                    .findVisibleSitemapBatch(afterId, now, PageRequest.of(0, BATCH_SIZE));
            for (ArticleRepository.SitemapRow article : batch) {
                writeUrl(xml, "/articles/" + segment(article.getSlug()));
                afterId = article.getId();
            }
            if (batch.size() < BATCH_SIZE) return;
        }
    }

    private void appendTopics(XMLStreamWriter xml) throws XMLStreamException {
        long afterId = 0;
        while (true) {
            List<TopicRepository.SitemapRow> batch = topicRepository
                    .findPublishedSitemapBatch(afterId, PageRequest.of(0, BATCH_SIZE));
            for (TopicRepository.SitemapRow topic : batch) {
                writeUrl(xml, "/topics/" + segment(topic.getSlug()));
                afterId = topic.getId();
            }
            if (batch.size() < BATCH_SIZE) return;
        }
    }

    private void appendTools(XMLStreamWriter xml, Instant now) throws XMLStreamException {
        long afterId = 0;
        while (true) {
            List<ToolRepository.SitemapRow> batch = toolRepository
                    .findVisibleSitemapBatch(afterId, now, PageRequest.of(0, BATCH_SIZE));
            for (ToolRepository.SitemapRow tool : batch) {
                writeUrl(xml, "/tools/" + segment(tool.getSlug()));
                afterId = tool.getId();
            }
            if (batch.size() < BATCH_SIZE) return;
        }
    }

    private void writeUrl(XMLStreamWriter xml, String path) throws XMLStreamException {
        xml.writeStartElement("url");
        xml.writeStartElement("loc");
        xml.writeCharacters(baseUrl + path);
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static String segment(String slug) {
        StringBuilder encoded = new StringBuilder();
        for (byte value : slug.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = value & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '.'
                    || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0x0f, 16)));
            }
        }
        return encoded.toString();
    }

    private static String validateOrigin(String configured) {
        try {
            URI uri = URI.create(configured == null ? "" : configured.trim());
            boolean rootPath = uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/");
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Canonical public base URL must be an absolute HTTPS URL");
            }
            if (!rootPath || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Canonical public base URL must be an HTTPS origin without a path, query, or fragment");
            }
            return new URI("https", null, uri.getHost(), uri.getPort(), null, null, null).toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Canonical public base URL must be an absolute HTTPS URL", exception);
        }
    }
}
