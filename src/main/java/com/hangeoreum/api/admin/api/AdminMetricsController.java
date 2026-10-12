package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.admin.application.AdminMetricsService;
import com.hangeoreum.api.admin.application.AdminMetricsService.Metrics;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService service;

    @GetMapping("/metrics")
    public Metrics metrics() {
        return service.metrics();
    }
}
