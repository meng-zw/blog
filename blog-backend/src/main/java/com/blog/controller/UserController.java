package com.blog.controller;

import com.blog.entity.User;
import com.blog.repository.UserRepository;
import com.blog.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器，处理用户注册和登录请求
 */
@RestController
@RequestMapping("/auth")
@Transactional
public class UserController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    /**
     * 用户注册
     * @param registerRequest 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        // 检查用户名是否已存在
        User existingUser = userRepository.findByUsername(registerRequest.getUsername());
        if (existingUser != null) {
            return ResponseEntity.badRequest().body("Username is already taken");
        }

        // 检查邮箱是否已存在
        User existingEmailUser = userRepository.findByEmail(registerRequest.getEmail());
        if (existingEmailUser != null) {
            return ResponseEntity.badRequest().body("Email is already in use");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEmail(registerRequest.getEmail());
        user.setPhone(registerRequest.getPhone());
        user.setRole("user"); // 默认角色为普通用户
        user.setAvatar("default-avatar.png"); // 默认头像

        // 保存用户
        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }

    /**
     * 用户登录
     * @param loginRequest 登录请求
     * @return 登录结果，包含JWT令牌
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        // 认证用户
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        // 设置认证上下文
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 生成JWT令牌
        String jwt = jwtUtils.generateToken(loginRequest.getUsername());

        // 构建响应
        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);
        response.put("tokenType", "Bearer");

        return ResponseEntity.ok(response);
    }

    /**
     * 注册请求DTO
     */
    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
        private String phone;

        // getter和setter方法
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    /**
     * 登录请求DTO
     */
    public static class LoginRequest {
        private String username;
        private String password;

        // getter和setter方法
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
