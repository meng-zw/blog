package com.blog.shared.error;

public class ConflictException extends RuntimeException {

    public ConflictException(String detail) {
        super(detail);
    }
}
