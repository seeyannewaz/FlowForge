package com.flowforge.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FlowForgeMetricsController {

    private final FlowForgeMetricsService metricsService;

    public FlowForgeMetricsController(FlowForgeMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/api/metrics/summary")
    public FlowForgeMetricsResponse summary() {
        return new FlowForgeMetricsResponse(
                metricsService.countJobsByStatus("QUEUED"),
                metricsService.countJobsByStatus("RUNNING"),
                metricsService.countJobsByStatus("SCHEDULED"),
                metricsService.countJobsByStatus("SUCCEEDED"),
                metricsService.countJobsByStatus("DEAD"),
                metricsService.countWorkers(),
                metricsService.countWorkersByStatus("IDLE"),
                metricsService.countWorkersByStatus("RUNNING"),
                metricsService.redisStreamLength()
        );
    }
}