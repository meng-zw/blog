package com.blog.media;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {
    private final MediaStorageService storageService;

    public MediaController(MediaStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/admin/media")
    public ResponseEntity<MediaUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        MediaAsset asset = storageService.store(file);
        return ResponseEntity.ok(new MediaUploadResponse(asset.getId(), asset.getStorageKey(), asset.getContentType(),
                asset.getWidth(), asset.getHeight(), "/api/media/" + asset.getStorageKey()));
    }

    @GetMapping("/media/{storageKey:.+}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String storageKey) {
        MediaAsset asset = storageService.findByStorageKey(storageKey);
        Path path = storageService.load(storageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }

    public record MediaUploadResponse(Long id, String storageKey, String contentType,
                                      Integer width, Integer height, String url) {
    }
}
