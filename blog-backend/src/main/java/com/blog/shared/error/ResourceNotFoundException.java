package com.blog.shared.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier);
    }
}
