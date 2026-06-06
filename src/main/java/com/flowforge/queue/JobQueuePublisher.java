package com.flowforge.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class JobQueuePublisher {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;

    public JobQueuePublisher(
            StringRedisTemplate redisTemplate,
            @Value("${flowforge.redis.stream-key}") String streamKey
    ) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
    }

    public void publish(UUID jobId) {
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(Map.of("jobId", jobId.toString()))
                .withStreamKey(streamKey);

        redisTemplate.opsForStream().add(record);
    }
}