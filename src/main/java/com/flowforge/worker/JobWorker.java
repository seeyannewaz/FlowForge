package com.flowforge.worker;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "flowforge.worker.enabled", havingValue = "true")
public class JobWorker {

    private final StringRedisTemplate redisTemplate;
    private final JobExecutionService executionService;
    private final String streamKey;
    private final String groupName;
    private final String workerId;

    public JobWorker(
            StringRedisTemplate redisTemplate,
            JobExecutionService executionService,
            @Value("${flowforge.redis.stream-key}") String streamKey,
            @Value("${flowforge.redis.group-name}") String groupName
    ) throws Exception {
        this.redisTemplate = redisTemplate;
        this.executionService = executionService;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.workerId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
    }

    @PostConstruct
    public void started() {
        System.out.println("FlowForge worker started with workerId=" + workerId);
        System.out.println("Listening to Redis stream=" + streamKey + ", group=" + groupName);
    }

    @Scheduled(fixedDelayString = "${flowforge.worker.poll-ms}")
    public void poll() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(groupName, workerId),
                    StreamReadOptions.empty()
                            .count(1)
                            .block(Duration.ofSeconds(2)),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );

            if (records == null || records.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> record : records) {
                System.out.println("Worker received Redis record: " + record.getId() + " -> " + record.getValue());

                Object jobIdValue = record.getValue().get("jobId");

                if (jobIdValue == null) {
                    System.out.println("Skipping non-job Redis record: " + record.getValue());
                    acknowledge(record);
                    continue;
                }

                try {
                    UUID jobId = UUID.fromString(jobIdValue.toString());
                    System.out.println("Executing job: " + jobId);

                    executionService.execute(jobId, workerId);

                    System.out.println("Finished job: " + jobId);
                    acknowledge(record);

                } catch (Exception e) {
                    System.err.println("Worker failed while executing job: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("Worker polling failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(streamKey, groupName, record.getId());
        System.out.println("Acknowledged Redis record: " + record.getId());
    }
}