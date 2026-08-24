package com.blog.article.dto;

import com.blog.article.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record ArticleWriteRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 160) @Pattern(regexp = "(?:|[a-z0-9]+(?:-[a-z0-9]+)*)") String slug,
        @NotBlank @Size(max = 500) String summary,
        @NotBlank @Size(max = 200000) String markdownContent,
        @NotNull ContentType contentType,
        @Positive Long coverMediaId,
        @Positive Long categoryId,
        @Positive Long topicId,
        @NotNull @Size(max = 50) Set<@NotNull @Positive Long> tagIds,
        @Size(max = 70) String seoTitle,
        @Size(max = 160) String seoDescription,
        @Size(max = 50) List<@NotNull @Positive Long> attachmentMediaIds) {
}
