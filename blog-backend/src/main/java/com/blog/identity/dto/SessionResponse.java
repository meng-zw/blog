package com.blog.identity.dto;

public record SessionResponse(boolean authenticated, String username, String displayName) {
    public static SessionResponse anonymous() {
        return new SessionResponse(false, null, null);
    }
}
