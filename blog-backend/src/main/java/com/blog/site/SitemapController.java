package com.blog.site;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {
    private static final String XML_UTF_8 = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8";

    private final SitemapService sitemapService;

    public SitemapController(SitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    @GetMapping(value = "/sitemap.xml", produces = XML_UTF_8)
    public String sitemap() {
        return sitemapService.generate();
    }
}
