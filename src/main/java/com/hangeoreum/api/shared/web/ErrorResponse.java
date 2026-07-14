package com.hangeoreum.api.shared.web;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, Object> details) {
}
