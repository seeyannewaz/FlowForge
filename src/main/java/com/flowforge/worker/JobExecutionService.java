package com.flowforge.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.handlers.JobHandler;
import com.flowforge.job.JobAttemptEntity;
import com.flowforge.job.JobAttemptRepository;
import com.flowforge.job.JobEntity;
import com.flowforge.job.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobExecutionService {

    private final JobRepository jobRepository;
    private final JobAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, JobHandler> handlersByType;
    private final TransactionTemplate transactionTemplate;

    public JobExecutionService(
            JobRepository jobRepository,
            JobAttemptRepository attemptRepository,
            ObjectMapper objectMapper,
            List<JobHandler> handlers,
            TransactionTemplate transactionTemplate) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(JobHandler::type, handler -> handler));
        this.transactionTemplate = transactionTemplate;
    }

    private boolean stillOwnsJobLock(JobEntity job, ExecutionContext context) {
        return "RUNNING".equals(job.getStatus())
                && context.workerId().equals(job.getLockedBy())
                && context.attemptNumber() == job.getAttemptCount()
                && job.getCompletedAt() == null;
    }

    public JobExecutionResult execute(UUID jobId, String workerId) {
        ExecutionContext context = transactionTemplate.execute(status -> startAttempt(jobId, workerId));

        if (context == null) {
            return new JobExecutionResult(jobId, false, "SKIPPED", null);
        }

        try {
            JobHandler handler = handlersByType.get(context.jobType());

            if (handler == null) {
                throw new IllegalArgumentException("No handler registered for job type: " + context.jobType());
            }

            Map<String, Object> payload = objectMapper.readValue(
                    context.payloadJson(),
                    new TypeReference<>() {
                    });

            Map<String, Object> result = handler.handle(payload);
            String resultJson = objectMapper.writeValueAsString(result);

            return transactionTemplate.execute(status -> completeSuccess(context, resultJson));

        } catch (Exception e) {
            return transactionTemplate.execute(status -> completeFailure(context, e));
        }
    }

    private ExecutionContext startAttempt(UUID jobId, String workerId) {
        JobEntity job = jobRepository.findById(jobId).orElse(null);

        if (job == null) {
            System.out.println("Skipping missing job: " + jobId);
            return null;
        }

        if (!job.getStatus().equals("QUEUED")) {
            System.out.println("Skipping job " + jobId + " because status is " + job.getStatus());
            return null;
        }

        int attemptNumber = job.getAttemptCount() + 1;

        JobAttemptEntity attempt = new JobAttemptEntity();
        attempt.setJobId(job.getId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setWorkerId(workerId);
        attempt.setStatus("RUNNING");
        attemptRepository.save(attempt);

        job.setStatus("RUNNING");
        job.setAttemptCount(attemptNumber);
        job.setLockedBy(workerId);
        job.setLockedAt(LocalDateTime.now());
        jobRepository.save(job);

        System.out.println("Started job " + jobId + " attempt " + attemptNumber + " on worker " + workerId);

        return new ExecutionContext(
                job.getId(),
                attempt.getId(),
                workerId,
                job.getType(),
                job.getPayload(),
                attemptNumber,
                job.getMaxAttempts());
    }

    private JobExecutionResult completeSuccess(ExecutionContext context, String resultJson) {
        JobEntity job = jobRepository.findById(context.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + context.jobId()));

        JobAttemptEntity attempt = attemptRepository.findById(context.attemptId())
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + context.attemptId()));

        if (!stillOwnsJobLock(job, context)) {
            String message = "Skipping stale success completion for job " + context.jobId()
                    + " because worker no longer owns the lock.";

            System.out.println(message);

            attempt.setStatus("STALE");
            attempt.setError(message);
            attempt.setFinishedAt(LocalDateTime.now());
            attemptRepository.save(attempt);

            return new JobExecutionResult(context.jobId(), false, "STALE_ATTEMPT", message);
        }

        job.setStatus("SUCCEEDED");
        job.setResult(resultJson);
        job.setCompletedAt(LocalDateTime.now());
        job.setLastError(null);
        job.setLockedBy(null);
        job.setLockedAt(null);

        attempt.setStatus("SUCCEEDED");
        attempt.setFinishedAt(LocalDateTime.now());

        jobRepository.save(job);
        attemptRepository.save(attempt);

        System.out.println("Succeeded job " + context.jobId());

        return new JobExecutionResult(context.jobId(), true, "SUCCEEDED", null);
    }

    private JobExecutionResult completeFailure(ExecutionContext context, Exception e) {
        JobEntity job = jobRepository.findById(context.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + context.jobId()));

        JobAttemptEntity attempt = attemptRepository.findById(context.attemptId())
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + context.attemptId()));

        if (!stillOwnsJobLock(job, context)) {
            String message = "Skipping stale failure completion for job " + context.jobId()
                    + " because worker no longer owns the lock.";

            System.out.println(message);

            attempt.setStatus("STALE");
            attempt.setError(message);
            attempt.setFinishedAt(LocalDateTime.now());
            attemptRepository.save(attempt);

            return new JobExecutionResult(context.jobId(), false, "STALE_ATTEMPT", message);
        }

        String errorMessage = e.getMessage() == null
                ? e.getClass().getSimpleName()
                : e.getMessage();

        job.setLastError(errorMessage);
        job.setLockedBy(null);
        job.setLockedAt(null);

        String finalStatus;

        if (context.attemptNumber() >= context.maxAttempts()) {
            job.setStatus("DEAD");
            job.setCompletedAt(LocalDateTime.now());
            finalStatus = "DEAD";
        } else {
            int delaySeconds = calculateBackoffSeconds(context.attemptNumber());

            job.setStatus("SCHEDULED");
            job.setNextRunAt(LocalDateTime.now().plusSeconds(delaySeconds));

            finalStatus = "SCHEDULED";

            System.out.println(
                    "Job " + job.getId() +
                            " failed on attempt " + context.attemptNumber() +
                            ". Retrying in " + delaySeconds + " seconds.");
        }

        attempt.setStatus("FAILED");
        attempt.setError(errorMessage);
        attempt.setFinishedAt(LocalDateTime.now());

        jobRepository.save(job);
        attemptRepository.save(attempt);

        return new JobExecutionResult(context.jobId(), true, finalStatus, errorMessage);
    }

    private int calculateBackoffSeconds(int attemptNumber) {
        int delay = (int) Math.pow(2, attemptNumber);
        return Math.min(delay, 60);
    }

    private record ExecutionContext(
            UUID jobId,
            Long attemptId,
            String workerId,
            String jobType,
            String payloadJson,
            int attemptNumber,
            int maxAttempts) {
    }
}