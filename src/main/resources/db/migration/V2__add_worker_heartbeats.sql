CREATE TABLE worker_heartbeats (
    worker_id VARCHAR(150) PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_job_id UUID,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_heartbeat_at TIMESTAMP NOT NULL DEFAULT NOW(),
    jobs_started INTEGER NOT NULL DEFAULT 0,
    jobs_succeeded INTEGER NOT NULL DEFAULT 0,
    jobs_failed INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT worker_heartbeats_status_check CHECK (
        status IN ('IDLE', 'RUNNING', 'STOPPED')
    )
);

CREATE INDEX idx_worker_heartbeats_last_heartbeat_at
ON worker_heartbeats(last_heartbeat_at);

CREATE INDEX idx_worker_heartbeats_current_job_id
ON worker_heartbeats(current_job_id);