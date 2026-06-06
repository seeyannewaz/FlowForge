package com.flowforge.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    List<JobEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<JobEntity> findByStatusAndNextRunAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
            String status,
            LocalDateTime now
    );

    long countByStatus(String status);

    @Query("""
            SELECT j
            FROM JobEntity j
            WHERE j.status = 'RUNNING'
              AND j.completedAt IS NULL
              AND j.lockedAt <= :cutoff
            ORDER BY j.lockedAt ASC
            """)
    List<JobEntity> findRecoverableRunningJobs(LocalDateTime cutoff);
}