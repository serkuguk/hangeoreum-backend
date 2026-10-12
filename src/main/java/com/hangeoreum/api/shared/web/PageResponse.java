package com.hangeoreum.api.shared.web;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(List<T> content, long totalElements, int page) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber());
    }
}
