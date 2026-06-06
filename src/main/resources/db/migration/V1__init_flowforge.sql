CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    result JSONB,
    status VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_run_at TIMESTAMP NOT NULL DEFAULT NOW(),
    locked_by VARCHAR(100),
    locked_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,

    CONSTRAINT jobs_status_check CHECK (
        status IN ('QUEUED', 'RUNNING', 'SCHEDULED', 'SUCCEEDED', 'FAILED', 'DEAD')
    )
);

CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_next_run_at ON jobs(next_run_at);
CREATE INDEX idx_jobs_created_at ON jobs(created_at);

CREATE TABLE job_attempts (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    worker_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE INDEX idx_job_attempts_job_id ON job_attempts(job_id);