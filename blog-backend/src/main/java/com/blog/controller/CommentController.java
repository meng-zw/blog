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
            return ResponseEntity.badRequest().body("User not found");
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setContent(commentDTO.getContent());
        comment.setUser(user);
        comment.setTargetId(id);
        comment.setTargetType("article");
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
            return ResponseEntity.badRequest().body("User not found");
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setContent(commentDTO.getContent());
        comment.setUser(user);
        comment.setTargetId(id);
        comment.setTargetType("tool");
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());

        // 保存评论
        commentRepository.save(comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * 评论DTO
     */
    public static class CommentDTO {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}