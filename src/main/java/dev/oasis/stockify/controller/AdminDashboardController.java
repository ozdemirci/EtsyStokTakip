package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public String showDashboard(Model model) {
        model.addAttribute("metrics", dashboardService.getDashboardMetrics());
        return "admin/dashboard";
    }


}
