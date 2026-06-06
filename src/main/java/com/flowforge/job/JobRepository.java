package com.flowforge.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    List<JobEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<JobEntity> findByStatusAndNextRunAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
            String status,
            LocalDateTime now
    );
}