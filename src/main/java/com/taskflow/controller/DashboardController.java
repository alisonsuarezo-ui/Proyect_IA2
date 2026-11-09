package com.taskflow.controller;

import com.taskflow.config.SecurityUtils;
import com.taskflow.dto.DashboardDto;
import com.taskflow.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardDto dashboard() {
        return dashboardService.build(SecurityUtils.currentUser());
    }
}
