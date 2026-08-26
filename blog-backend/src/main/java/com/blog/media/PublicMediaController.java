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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Stable public media endpoints preserve provider-neutral URLs and error responses. */
@RestController
@RequestMapping("/media/assets")
public class PublicMediaController {
    private final MediaApplicationService mediaApplicationService;

    public PublicMediaController(MediaApplicationService mediaApplicationService) {
        this.mediaApplicationService = mediaApplicationService;
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<StreamingResponseBody> open(@PathVariable long mediaId) {
        MediaApplicationService.PublicMediaAsset asset = mediaApplicationService.resolvePublic(mediaId);
        if (asset instanceof MediaApplicationService.PublicMediaRedirect redirect) {
            return redirect(redirect);
        }
        if (asset instanceof MediaApplicationService.PublicMediaContent content) {
            return inline(content);
        }
        throw new IllegalStateException("Unsupported public media response");
    }

    @GetMapping("/{mediaId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable long mediaId) {
        MediaApplicationService.PublicMediaContent asset = mediaApplicationService.openPublicDownload(mediaId);
        StreamingResponseBody body = outputStream -> {
            try (var content = asset.content()) {
                content.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(asset.contentType()))
                .contentLength(asset.byteSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(asset.filename(), StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private static ResponseEntity<StreamingResponseBody> redirect(MediaApplicationService.PublicMediaRedirect asset) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND).location(asset.location())
                .cacheControl(CacheControl.noStore());
        return response.build();
    }

    private static ResponseEntity<StreamingResponseBody> inline(MediaApplicationService.PublicMediaContent asset) {
        StreamingResponseBody body = outputStream -> {
            try (var content = asset.content()) {
                content.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(asset.contentType()))
                .contentLength(asset.byteSize())
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(body);
    }
}
