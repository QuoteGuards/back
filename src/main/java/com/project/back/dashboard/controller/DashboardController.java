package com.project.back.dashboard.controller;

import com.project.back.dashboard.dto.DashboardSummaryResponse;
import com.project.back.dashboard.dto.SalesAnalysisResponse;
import com.project.back.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class DashboardController {


    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/sales-analysis")
    public SalesAnalysisResponse getSalesAnalysis() {
        return dashboardService.getSalesAnalysis();
    }
}
