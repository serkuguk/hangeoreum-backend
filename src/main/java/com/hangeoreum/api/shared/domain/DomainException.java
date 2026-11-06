package com.hangeoreum.api.shared.domain;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {
    public enum Category { VALIDATION, CONFLICT }

    private final Category category;

    private DomainException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public static DomainException validation(String message) {
        return new DomainException(Category.VALIDATION, message);
    }

    public static DomainException conflict(String message) {
        return new DomainException(Category.CONFLICT, message);
    }
}
