package com.hangeoreum.api.gamification.api;

import com.hangeoreum.api.gamification.application.DashboardService;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Gamification")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard() {
        return dashboardService.getDashboard(CurrentUser.id());
    }

    @GetMapping("/profile")
    public DashboardService.ProfileDto profile() {
        return dashboardService.getProfile(CurrentUser.id());
    }

    @GetMapping("/achievements")
    public List<DashboardService.AchievementDto> achievements() {
        return dashboardService.getAchievements(CurrentUser.id());
    }
}
