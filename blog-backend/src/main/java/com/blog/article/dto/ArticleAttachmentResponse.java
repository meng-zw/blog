package com.blog.article.dto;

public record ArticleAttachmentResponse(Long mediaId, String displayName, String contentType, long byteSize,
                                        String downloadUrl) {
}
