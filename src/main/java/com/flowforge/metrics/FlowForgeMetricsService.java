package com.flowforge.metrics;

import com.flowforge.job.JobRepository;
import com.flowforge.worker.WorkerHeartbeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowForgeMetricsService {

    private final JobRepository jobRepository;
    private final WorkerHeartbeatRepository workerRepository;
    private final StringRedisTemplate redisTemplate;
    private final String streamKey;

    public FlowForgeMetricsService(
            JobRepository jobRepository,
            WorkerHeartbeatRepository workerRepository,
            StringRedisTemplate redisTemplate,
            @Value("${flowforge.redis.stream-key}") String streamKey
    ) {
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
    }

    @Transactional(readOnly = true)
    public long countJobsByStatus(String status) {
        return jobRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countWorkersByStatus(String status) {
        return workerRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countWorkers() {
        return workerRepository.count();
    }

    public long redisStreamLength() {
        Long size = redisTemplate.opsForStream().size(streamKey);
        return size == null ? 0L : size;
    }
}