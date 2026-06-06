package com.flowforge.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlowForgeMetricsConfiguration {

    @Bean
    public ApplicationRunner registerFlowForgeMetrics(
            MeterRegistry registry,
            FlowForgeMetricsService metricsService
    ) {
        return args -> {
            Gauge.builder("flowforge.jobs.queued", metricsService,
                            service -> service.countJobsByStatus("QUEUED"))
                    .description("Number of queued jobs")
                    .register(registry);

            Gauge.builder("flowforge.jobs.running", metricsService,
                            service -> service.countJobsByStatus("RUNNING"))
                    .description("Number of running jobs")
                    .register(registry);

            Gauge.builder("flowforge.jobs.scheduled", metricsService,
                            service -> service.countJobsByStatus("SCHEDULED"))
                    .description("Number of scheduled retry jobs")
                    .register(registry);

            Gauge.builder("flowforge.jobs.succeeded", metricsService,
                            service -> service.countJobsByStatus("SUCCEEDED"))
                    .description("Number of succeeded jobs")
                    .register(registry);

            Gauge.builder("flowforge.jobs.dead", metricsService,
                            service -> service.countJobsByStatus("DEAD"))
                    .description("Number of dead jobs")
                    .register(registry);

            Gauge.builder("flowforge.workers.total", metricsService,
                            FlowForgeMetricsService::countWorkers)
                    .description("Total number of registered workers")
                    .register(registry);

            Gauge.builder("flowforge.workers.idle", metricsService,
                            service -> service.countWorkersByStatus("IDLE"))
                    .description("Number of idle workers")
                    .register(registry);

            Gauge.builder("flowforge.workers.running", metricsService,
                            service -> service.countWorkersByStatus("RUNNING"))
                    .description("Number of running workers")
                    .register(registry);

            Gauge.builder("flowforge.redis.stream.length", metricsService,
                            FlowForgeMetricsService::redisStreamLength)
                    .description("Redis Stream length for FlowForge jobs")
                    .register(registry);
        };
    }
}