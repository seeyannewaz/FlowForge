package com.flowforge.worker;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerHeartbeatRepository repository;

    public WorkerController(WorkerHeartbeatRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<WorkerHeartbeatResponse> listWorkers() {
        return repository.findAllByOrderByLastHeartbeatAtDesc()
                .stream()
                .map(WorkerHeartbeatResponse::from)
                .toList();
    }
}