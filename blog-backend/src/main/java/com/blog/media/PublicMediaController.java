package com.blog.media;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Stable public media IDs redirect to the currently configured object provider. */
@RestController
@RequestMapping("/media/assets")
public class PublicMediaController {
    private final MediaApplicationService mediaApplicationService;

    public PublicMediaController(MediaApplicationService mediaApplicationService) {
        this.mediaApplicationService = mediaApplicationService;
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<Void> open(@PathVariable long mediaId) {
        return redirect(mediaApplicationService.resolvePublic(mediaId), false);
    }

    @GetMapping("/{mediaId}/download")
    public ResponseEntity<Void> download(@PathVariable long mediaId) {
        return redirect(mediaApplicationService.resolvePublic(mediaId), true);
    }

    private static ResponseEntity<Void> redirect(MediaApplicationService.PublicMediaAsset asset, boolean download) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND).location(asset.location())
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        if (download) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(asset.filename(), StandardCharsets.UTF_8).build().toString());
        }
        return response.build();
    }
}
