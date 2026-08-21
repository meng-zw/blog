package com.blog.controller;

import com.blog.entity.Comment;
import com.blog.entity.User;
import com.blog.repository.CommentRepository;
import com.blog.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 评论控制器，处理文章和工具的评论
 */
@RestController
@Transactional
public class CommentController {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取文章评论列表
     * @param id 文章ID
     * @return 评论列表
     */
    @GetMapping("/articles/{id}/comments")
    public ResponseEntity<?> getArticleComments(@PathVariable("id") Long id) {
        List<Comment> comments = commentRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(id, "article");
        return ResponseEntity.ok(comments);
    }

    /**
     * 创建文章评论（需要登录）
     * @param id 文章ID
     * @param commentDTO 评论内容
     * @return 创建的评论
     */
    @PostMapping("/articles/{id}/comments")
    public ResponseEntity<?> createArticleComment(@PathVariable("id") Long id, @RequestBody CommentDTO commentDTO) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询用户
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body(errorBody("用户不存在"));
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setContent(commentDTO.getContent());
        comment.setUser(user);
        comment.setTargetId(id);
        comment.setTargetType("article");
        if (commentDTO.getParentId() != null) {
            Comment parent = commentRepository.findById(commentDTO.getParentId()).orElse(null);
            if (parent != null) {
                comment.setParent(parent);
            }
        }
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());

        // 保存评论
        commentRepository.save(comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * 获取工具评论列表
     * @param id 工具ID
     * @return 评论列表
     */
    @GetMapping("/tools/{id}/comments")
    public ResponseEntity<?> getToolComments(@PathVariable("id") Long id) {
        List<Comment> comments = commentRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(id, "tool");
        return ResponseEntity.ok(comments);
    }

    /**
     * 创建工具评论（需要登录）
     * @param id 工具ID
     * @param commentDTO 评论内容
     * @return 创建的评论
     */
    @PostMapping("/tools/{id}/comments")
    public ResponseEntity<?> createToolComment(@PathVariable("id") Long id, @RequestBody CommentDTO commentDTO) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询用户
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body(errorBody("用户不存在"));
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setContent(commentDTO.getContent());
        comment.setUser(user);
        comment.setTargetId(id);
        comment.setTargetType("tool");
        if (commentDTO.getParentId() != null) {
            Comment parent = commentRepository.findById(commentDTO.getParentId()).orElse(null);
            if (parent != null) {
                comment.setParent(parent);
            }
        }
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());

        // 保存评论
        commentRepository.save(comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * 删除评论（仅作者或管理员）
     * @param id 评论ID
     */
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable("id") Long id) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Comment comment = commentRepository.findById(id).orElse(null);
        if (comment == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!comment.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("没有权限删除此评论"));
        }

        commentRepository.deleteById(id);
        return ResponseEntity.ok(errorBody("评论删除成功"));
    }

    /**
     * 构造统一的错误响应体，保证前端能从 message 字段读到具体原因
     */
    private java.util.Map<String, String> errorBody(String message) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("message", message);
        return body;
    }

    /**
     * 获取评论回复列表
     * @param parentId 父评论ID
     * @return 回复列表
     */
    @GetMapping("/comments/{parentId}/replies")
    public ResponseEntity<?> getReplyComments(@PathVariable("parentId") Long parentId) {
        List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtDesc(parentId);
        return ResponseEntity.ok(replies);
    }

    /**
     * 评论DTO
     */
    public static class CommentDTO {
        private String content;
        private Long parentId;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }
    }
}