package com.blog.media;

import com.blog.media.dto.MediaResponse;
import com.blog.media.dto.MediaUploadPlanResponse;
import com.blog.media.dto.MediaUploadRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import com.blog.shared.error.TooManyRequestsException;

/** Administrative transport layer for the provider-neutral media upload protocol. */
@RestController
@RequestMapping("/admin/media")
public class AdminMediaController {
    private final MediaApplicationService mediaApplicationService;
    private final MediaUploadPlanRateLimiter uploadPlanRateLimiter;

    public AdminMediaController(MediaApplicationService mediaApplicationService,
                                MediaUploadPlanRateLimiter uploadPlanRateLimiter) {
        this.mediaApplicationService = mediaApplicationService;
        this.uploadPlanRateLimiter = uploadPlanRateLimiter;
    }

    @PostMapping("/uploads")
    public MediaUploadPlanResponse requestUpload(@Valid @RequestBody MediaUploadRequest request, Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        if (!uploadPlanRateLimiter.tryAcquire(authentication.getName(), servletRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("上传请求过于频繁，请稍后重试");
        }
        return mediaApplicationService.requestUpload(request, authentication.getName());
    }

    @GetMapping
    public com.blog.shared.web.PageResponse<com.blog.media.dto.AdminMediaAssetResponse> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) MediaStatus status, @RequestParam(required = false) MediaPurpose purpose) {
        return mediaApplicationService.list(page, size, status, purpose);
    }

    @PutMapping(value = "/uploads/{mediaId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadMultipart(@PathVariable long mediaId, @RequestPart("file") MultipartFile file,
                                                Authentication authentication) {
        try {
            mediaApplicationService.uploadProxyContent(mediaId, authentication.getName(), file.getInputStream());
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded media", exception);
        }
    }

    @PutMapping("/uploads/{mediaId}/content")
    public ResponseEntity<Void> uploadBinary(@PathVariable long mediaId, Authentication authentication,
                                             HttpServletRequest request) {
        try {
            mediaApplicationService.uploadProxyContent(mediaId, authentication.getName(), request.getInputStream());
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded media", exception);
        }
    }

    @PostMapping("/{mediaId}/complete")
    public MediaResponse complete(@PathVariable long mediaId, Authentication authentication) {
        return mediaApplicationService.complete(mediaId, authentication.getName());
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable long mediaId, Authentication authentication) {
        mediaApplicationService.delete(mediaId, authentication.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
