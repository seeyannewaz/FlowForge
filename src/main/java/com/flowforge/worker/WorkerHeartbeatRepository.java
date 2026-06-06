package com.flowforge.worker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeatEntity, String> {
    List<WorkerHeartbeatEntity> findAllByOrderByLastHeartbeatAtDesc();

    List<WorkerHeartbeatEntity> findByLastHeartbeatAtBefore(LocalDateTime cutoff);

    long countByStatus(String status);
}