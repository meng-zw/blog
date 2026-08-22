package com.blog.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器，支持文章配图、封面图等图片上传
 */
@RestController
@RequestMapping("/files")
public class FileController {

    /**
     * 上传目录（相对后端工作目录）
     */
    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 允许的图片类型
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp"
    );

    /**
     * 单文件大小上限（10MB）
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 上传图片
     * @param file 图片文件
     * @return 访问URL，格式：{ "url": "/api/uploads/202608/xxx.png" }
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        // 校验文件是否为空
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("请选择要上传的文件"));
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(errorBody("仅支持 PNG/JPG/GIF/WebP/BMP 格式的图片"));
        }

        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(errorBody("图片大小不能超过 10MB"));
        }

        // 按月份归档存储：uploads/yyyyMM/uuid.ext
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            Path monthDir = Paths.get(uploadDir, month);
            Files.createDirectories(monthDir);
            Path target = monthDir.resolve(filename);
            file.transferTo(target.toAbsolutePath());

            // 返回可访问的URL（由静态资源映射 /uploads/** 提供访问）
            Map<String, String> result = new HashMap<>();
            result.put("url", "/api/uploads/" + month + "/" + filename);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(errorBody("文件保存失败，请稍后重试"));
        }
    }

    /**
     * 构造统一的错误响应体
     */
    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
