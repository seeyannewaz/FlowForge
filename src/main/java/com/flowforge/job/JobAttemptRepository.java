package com.flowforge.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAttemptRepository extends JpaRepository<JobAttemptEntity, Long> {
}