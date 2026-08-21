package com.blog.controller;

import com.blog.entity.Article;
import com.blog.entity.Tool;
import com.blog.repository.ArticleRepository;
import com.blog.repository.ToolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索控制器，提供文章和工具的搜索功能
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ToolRepository toolRepository;

    /**
     * 搜索文章
     * @param keyword 搜索关键词
     * @return 匹配的文章列表
     */
    @GetMapping("/articles")
    public ResponseEntity<?> searchArticles(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        
        List<Article> articles = articleRepository.searchByKeyword(keyword.trim());
        return ResponseEntity.ok(articles);
    }

    /**
     * 搜索工具
     * @param keyword 搜索关键词
     * @return 匹配的工具列表
     */
    @GetMapping("/tools")
    public ResponseEntity<?> searchTools(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        
        List<Tool> tools = toolRepository.searchByKeyword(keyword.trim());
        return ResponseEntity.ok(tools);
    }

    /**
     * 全站搜索（同时搜索文章和工具）
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    @GetMapping("/all")
    public ResponseEntity<?> searchAll(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(new SearchResult());
        }
        
        List<Article> articles = articleRepository.searchByKeyword(keyword.trim());
        List<Tool> tools = toolRepository.searchByKeyword(keyword.trim());
        
        SearchResult result = new SearchResult();
        result.setArticles(articles);
        result.setTools(tools);
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索结果包装类
     */
    public static class SearchResult {
        private List<Article> articles;
        private List<Tool> tools;

        public List<Article> getArticles() {
            return articles;
        }

        public void setArticles(List<Article> articles) {
            this.articles = articles;
        }

        public List<Tool> getTools() {
            return tools;
        }

        public void setTools(List<Tool> tools) {
            this.tools = tools;
        }
    }
}
