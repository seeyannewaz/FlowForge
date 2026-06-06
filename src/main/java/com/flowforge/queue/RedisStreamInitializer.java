package com.flowforge.queue;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisStreamInitializer {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String groupName;

    public RedisStreamInitializer(
            StringRedisTemplate redisTemplate,
            @Value("${flowforge.redis.stream-key}") String streamKey,
            @Value("${flowforge.redis.group-name}") String groupName
    ) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.groupName = groupName;
    }

    @PostConstruct
    public void initialize() {
        try {
            Boolean exists = redisTemplate.hasKey(streamKey);

            if (exists == null || !exists) {
                redisTemplate.opsForStream().add(
                        StreamRecords.mapBacked(Map.of("init", "true"))
                                .withStreamKey(streamKey)
                );
            }

            redisTemplate.opsForStream().createGroup(
                    streamKey,
                    ReadOffset.from("0-0"),
                    groupName
            );

            System.out.println("Redis stream group created: " + groupName);

        } catch (RedisSystemException e) {
            if (isBusyGroupError(e)) {
                System.out.println("Redis stream group already exists: " + groupName);
                return;
            }

            throw e;
        }
    }

    private boolean isBusyGroupError(Throwable throwable) {
        while (throwable != null) {
            String message = throwable.getMessage();

            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }

            throwable = throwable.getCause();
        }

        return false;
    }
}