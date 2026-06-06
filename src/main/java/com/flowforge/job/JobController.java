package com.flowforge.job;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public JobResponse submitJob(@Valid @RequestBody SubmitJobRequest request) {
        return JobResponse.from(jobService.submitJob(request));
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return JobResponse.from(jobService.getJob(id));
    }

    @GetMapping
    public List<JobResponse> listJobs(@RequestParam(required = false) String status) {
        return jobService.listJobs(status)
                .stream()
                .map(JobResponse::from)
                .toList();
    }
}