# FlowForge

FlowForge is a distributed task orchestration engine built with **Java, Spring Boot, PostgreSQL, Redis Streams, and Docker**. It supports asynchronous job submission, background worker execution, automatic retries, exponential backoff, worker heartbeats, and stuck job recovery.

The project is designed to demonstrate production-grade backend engineering concepts such as reliable queue processing, transactional persistence, failure recovery, and worker lifecycle tracking.

---

## Features

- Asynchronous job submission through REST APIs
- PostgreSQL-backed job persistence and execution history
- Redis Streams-based job dispatch
- Background worker execution with consumer groups
- Job lifecycle tracking: `QUEUED`, `RUNNING`, `SCHEDULED`, `SUCCEEDED`, `FAILED`, `DEAD`
- Job attempt history for debugging and observability
- Automatic retries with exponential backoff
- Dead-state handling after max retry attempts
- Transaction-safe Redis publishing after database commit
- Worker heartbeat tracking
- Stuck `RUNNING` job recovery
- Lock ownership checks to prevent zombie worker completions
- Worker inspection API

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot |
| Queue | Redis Streams |
| Database | PostgreSQL |
| ORM / Persistence | Spring Data JPA, Hibernate |
| Migrations | Flyway |
| Containerization | Docker, Docker Compose |
| APIs | REST |
| Monitoring Base | Spring Boot Actuator |

---

## Architecture

```text
Client / curl / frontend
        |
        v
FlowForge API Service
        |
        | 1. Save job metadata
        v
PostgreSQL
        |
        | 2. Publish job ID after DB commit
        v
Redis Stream: flowforge.jobs
        |
        | 3. Worker consumes through Redis consumer group
        v
FlowForge Worker
        |
        | 4. Execute job handler
        v
Update PostgreSQL with result, status, attempts, and worker info
```

### Reliability Flow

```text
Job Submitted
    |
    v
QUEUED
    |
    v
RUNNING
    |
    +--> SUCCEEDED
    |
    +--> SCHEDULED retry with exponential backoff
    |
    +--> DEAD after max attempts
```

### Worker Recovery Flow

```text
Worker starts job
    |
    v
Job marked RUNNING with locked_by and locked_at
    |
    v
Worker sends periodic heartbeat
    |
    v
If heartbeat stops and job stays RUNNING too long
    |
    v
Recovery scheduler marks job SCHEDULED or DEAD
    |
    v
Retry scheduler requeues job if attempts remain
```

---

## Project Structure

```text
flowforge/
├── src/
│   └── main/
│       ├── java/com/flowforge/
│       │   ├── FlowForgeApplication.java
│       │   ├── handlers/              # Job handlers such as echo, sleep, fail
│       │   ├── job/                   # Job entities, repositories, services, controllers
│       │   ├── queue/                 # Redis Stream publisher and initializer
│       │   └── worker/                # Worker, heartbeat, retry, and recovery logic
│       └── resources/
│           ├── application.yml
│           └── db/migration/          # Flyway database migrations
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Prerequisites

Install the following before running the project:

- Java 21
- Maven 3.9+
- Docker Desktop
- Git

Check your versions:

```bash
java --version
mvn -v
docker --version
```

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/flowforge.git
cd flowforge
```

### 2. Start PostgreSQL and Redis

```bash
docker compose up -d
```

Verify containers are running:

```bash
docker ps
```

You should see containers for:

```text
flowforge-postgres
flowforge-redis
```

### 3. Run the Spring Boot application

```bash
mvn spring-boot:run
```

The app starts at:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

---

## Configuration

The main configuration is in:

```text
src/main/resources/application.yml
```

Example configuration:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/flowforge
    username: flowforge
    password: flowforge

  data:
    redis:
      host: localhost
      port: 6379

flowforge:
  redis:
    stream-key: flowforge.jobs
    group-name: flowforge-workers

  worker:
    enabled: true
    poll-ms: 1000
    heartbeat-ms: 5000

  recovery:
    scan-ms: 5000
    running-timeout-seconds: 30
    heartbeat-timeout-seconds: 15
```

> Note: If your local Docker Compose maps PostgreSQL as `5432:5432`, use port `5432` in `application.yml`. If you changed it to `5433:5432` to avoid a local PostgreSQL conflict, keep `5433`.

---

## API Usage

### Submit an Echo Job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "echo",
    "payload": {
      "message": "Hello from FlowForge"
    },
    "priority": 1,
    "maxAttempts": 3
  }'
```

Example response:

```json
{
  "id": "ccbee384-9a46-4944-af9d-56d7bab18891",
  "type": "echo",
  "payload": "{\"message\":\"Hello from FlowForge\"}",
  "result": null,
  "status": "QUEUED",
  "priority": 1,
  "attemptCount": 0,
  "maxAttempts": 3,
  "lastError": null
}
```

---

### Submit a Sleep Job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "sleep",
    "payload": {
      "milliseconds": 3000
    },
    "priority": 1,
    "maxAttempts": 3
  }'
```

This job simulates a longer-running background task.

---

### Submit a Failing Job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "fail",
    "payload": {},
    "priority": 1,
    "maxAttempts": 3
  }'
```

This job intentionally fails so retry and dead-state handling can be tested.

---

### Get Job by ID

```bash
curl http://localhost:8080/api/jobs/YOUR_JOB_ID
```

Example successful job:

```json
{
  "id": "ccbee384-9a46-4944-af9d-56d7bab18891",
  "type": "echo",
  "payload": "{\"message\":\"Hello from FlowForge\"}",
  "result": "{\"message\":\"Hello from FlowForge\",\"processedAt\":\"2026-06-05T22:22:39\"}",
  "status": "SUCCEEDED",
  "priority": 1,
  "attemptCount": 1,
  "maxAttempts": 3,
  "lastError": null
}
```

---

### List Jobs

```bash
curl http://localhost:8080/api/jobs
```

Filter by status:

```bash
curl "http://localhost:8080/api/jobs?status=SUCCEEDED"
```

---

### List Workers

```bash
curl http://localhost:8080/api/workers
```

Example response:

```json
[
  {
    "workerId": "DESKTOP-1234-36d6fd6d-0ef3-49c6-b2a4-1d0e624e8d8c",
    "hostname": "DESKTOP-1234",
    "status": "IDLE",
    "currentJobId": null,
    "startedAt": "2026-06-05T21:30:10",
    "lastHeartbeatAt": "2026-06-05T21:31:20",
    "jobsStarted": 5,
    "jobsSucceeded": 4,
    "jobsFailed": 1
  }
]
```

---

## Job Types

### `echo`

Returns the provided message.

Payload:

```json
{
  "message": "Hello from FlowForge"
}
```

### `sleep`

Sleeps for the provided number of milliseconds.

Payload:

```json
{
  "milliseconds": 3000
}
```

### `fail`

Always fails intentionally. Used to test retries and dead-state handling.

Payload:

```json
{}
```

---

## Database Tables

### `jobs`

Stores the main job state.

Important fields:

| Column | Purpose |
|---|---|
| `id` | Job UUID |
| `type` | Job handler type |
| `payload` | JSON input |
| `result` | JSON output |
| `status` | Current job state |
| `attempt_count` | Number of attempts so far |
| `max_attempts` | Maximum allowed attempts |
| `next_run_at` | Retry schedule time |
| `locked_by` | Worker currently processing job |
| `locked_at` | When the worker claimed the job |
| `last_error` | Most recent failure |
| `completed_at` | Completion timestamp |

### `job_attempts`

Stores attempt-level execution history.

Important fields:

| Column | Purpose |
|---|---|
| `job_id` | Parent job ID |
| `attempt_number` | Attempt count |
| `worker_id` | Worker that ran the attempt |
| `status` | Attempt result |
| `error` | Failure message |
| `started_at` | Attempt start time |
| `finished_at` | Attempt finish time |

### `worker_heartbeats`

Tracks live workers.

Important fields:

| Column | Purpose |
|---|---|
| `worker_id` | Unique worker identifier |
| `hostname` | Host running the worker |
| `status` | `IDLE`, `RUNNING`, or `STOPPED` |
| `current_job_id` | Job currently being processed |
| `last_heartbeat_at` | Last heartbeat timestamp |
| `jobs_started` | Total jobs started |
| `jobs_succeeded` | Total jobs succeeded |
| `jobs_failed` | Total jobs failed |

---

## Testing Stuck Job Recovery

To simulate a crashed worker, insert a stale `RUNNING` job directly into PostgreSQL.

Open Postgres:

```bash
docker exec -it flowforge-postgres psql -U flowforge -d flowforge
```

Run:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO jobs (
    id,
    type,
    payload,
    status,
    priority,
    attempt_count,
    max_attempts,
    next_run_at,
    locked_by,
    locked_at,
    last_error,
    created_at,
    updated_at
)
VALUES (
    gen_random_uuid(),
    'echo',
    '{"message": "Recovered from a simulated crashed worker"}'::jsonb,
    'RUNNING',
    1,
    1,
    3,
    (NOW() AT TIME ZONE 'America/Los_Angeles'),
    'dead-worker-1',
    (NOW() AT TIME ZONE 'America/Los_Angeles') - INTERVAL '2 minutes',
    'Simulated worker crash',
    (NOW() AT TIME ZONE 'America/Los_Angeles'),
    (NOW() AT TIME ZONE 'America/Los_Angeles')
)
RETURNING id;
```

Copy the returned ID, exit Postgres:

```sql
\q
```

Wait a few seconds, then check the job:

```bash
curl http://localhost:8080/api/jobs/YOUR_RETURNED_ID
```

Expected progression:

```text
RUNNING stale
-> SCHEDULED
-> QUEUED
-> RUNNING
-> SUCCEEDED
```

---

## Key Engineering Decisions

### Transaction-safe queue publishing

FlowForge publishes jobs to Redis only after the PostgreSQL transaction commits. This prevents workers from reading a job ID before the database row is visible.

```text
Save job in PostgreSQL
        |
        v
Commit transaction
        |
        v
Publish job ID to Redis Stream
```

### Redis Streams consumer groups

Workers consume jobs from a Redis Stream using a shared consumer group. This allows multiple workers to process jobs while maintaining reliable delivery semantics.

### Exponential backoff retries

Failed jobs are not immediately retried. Instead, they are scheduled for a future retry using exponential backoff.

```text
Attempt 1 failed -> retry after 2 seconds
Attempt 2 failed -> retry after 4 seconds
Attempt 3 failed -> retry after 8 seconds
```

### Worker heartbeats

Each worker periodically updates its heartbeat row in PostgreSQL. If a worker stops sending heartbeats while holding a job lock, the recovery scheduler can detect the stale worker.

### Zombie worker protection

Before completing a job, a worker verifies that it still owns the job lock. This prevents stale workers from overwriting results after recovery has already requeued or reassigned the job.

---

## Common Commands

Start dependencies:

```bash
docker compose up -d
```

Stop dependencies:

```bash
docker compose down
```

Reset local database and Redis:

```bash
docker compose down -v
docker compose up -d
```

Run app:

```bash
mvn spring-boot:run
```

Check Postgres tables:

```bash
docker exec -it flowforge-postgres psql -U flowforge -d flowforge -c "\dt"
```

Check Redis stream length:

```bash
docker exec -it flowforge-redis redis-cli XLEN flowforge.jobs
```

Inspect Redis stream:

```bash
docker exec -it flowforge-redis redis-cli XRANGE flowforge.jobs - +
```

Inspect Redis consumer group:

```bash
docker exec -it flowforge-redis redis-cli XINFO GROUPS flowforge.jobs
```

---

## Current Status

Implemented:

- Async job submission
- PostgreSQL persistence
- Redis Streams dispatch
- Worker execution
- Job attempt history
- Success/failure/dead states
- Automatic retries
- Exponential backoff
- Transaction-safe queue publishing
- Worker heartbeats
- Stuck `RUNNING` job recovery
- Zombie worker lock protection
- Worker inspection API

