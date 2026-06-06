# FlowForge

FlowForge is a distributed task orchestration engine built with **Java 21, Spring Boot, PostgreSQL, Redis Streams, Docker, Docker Compose, and Micrometer**. It supports asynchronous job submission, background worker execution, automatic retries, exponential backoff, worker heartbeats, stuck job recovery, and application/container-level observability.

The project is designed to demonstrate production-grade backend engineering concepts such as reliable queue processing, transactional persistence, distributed worker recovery, containerized deployment, and operational metrics.

---

## Features

- Asynchronous job submission through REST APIs
- PostgreSQL-backed job persistence and execution history
- Redis Streams-based job dispatch
- Background worker execution with Redis consumer groups
- Job lifecycle tracking: `QUEUED`, `RUNNING`, `SCHEDULED`, `SUCCEEDED`, `FAILED`, `DEAD`
- Job attempt history for debugging and observability
- Automatic retries with exponential backoff
- Dead-state handling after max retry attempts
- Transaction-safe Redis publishing after database commit
- Worker heartbeat tracking
- Stuck `RUNNING` job recovery
- Lock ownership checks to prevent zombie worker completions
- Worker inspection API
- Dockerized Spring Boot application
- One-command local deployment with Docker Compose
- Actuator health endpoint
- Prometheus-compatible metrics endpoint
- Custom FlowForge metrics for jobs, workers, and Redis stream depth
- JSON metrics summary API

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
| Metrics / Monitoring | Spring Boot Actuator, Micrometer, Prometheus Registry |
| APIs | REST |

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

### Docker Compose Architecture

```text
Docker Compose Network: flowforge_default

+----------------------+       +----------------------+
| flowforge-app        | ----> | flowforge-postgres   |
| Spring Boot API      |       | PostgreSQL           |
| Worker Scheduler     |       | Port: 5432 internal  |
| Metrics Endpoints    |       | Port: 5433 host      |
+----------------------+       +----------------------+
          |
          v
+----------------------+
| flowforge-redis      |
| Redis Streams Queue  |
| Port: 6379           |
+----------------------+
```

The Spring Boot app connects to dependencies differently depending on where it is running:

| Runtime | PostgreSQL URL | Redis Host |
|---|---|---|
| Local Maven / local JAR | `jdbc:postgresql://localhost:5433/flowforge` | `localhost` |
| Docker Compose app container | `jdbc:postgresql://postgres:5432/flowforge` | `redis` |

---

## Reliability Flow

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

---

## Worker Recovery Flow

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

## Metrics Flow

```text
FlowForge app
    |
    +--> /actuator/health
    |
    +--> /actuator/prometheus
    |
    +--> /api/metrics/summary
```

Custom metrics include:

```text
flowforge_jobs_queued
flowforge_jobs_running
flowforge_jobs_scheduled
flowforge_jobs_succeeded
flowforge_jobs_dead
flowforge_workers_total
flowforge_workers_idle
flowforge_workers_running
flowforge_redis_stream_length
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
│       │   ├── metrics/               # Metrics service, gauges, and summary API
│       │   ├── queue/                 # Redis Stream publisher and initializer
│       │   └── worker/                # Worker, heartbeat, retry, and recovery logic
│       └── resources/
│           ├── application.yml
│           └── db/migration/          # Flyway database migrations
├── Dockerfile                         # Multi-stage Spring Boot container build
├── .dockerignore                      # Keeps Docker build context clean
├── docker-compose.yml                 # App + PostgreSQL + Redis orchestration
├── pom.xml
└── README.md
```

---

## Prerequisites

For the easiest setup, install:

- Docker Desktop
- Git

For local non-Docker development, also install:

- Java 21
- Maven 3.9+

Check versions:

```bash
java --version
mvn -v
docker --version
docker compose version
```

---

## Getting Started: Full Docker Compose Setup

This is the recommended way to run FlowForge.

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/flowforge.git
cd flowforge
```

### 2. Build and run all services

```bash
docker compose up --build
```

This starts:

```text
flowforge-app
flowforge-postgres
flowforge-redis
```

To run in the background:

```bash
docker compose up --build -d
```

### 3. Check app logs

```bash
docker logs -f flowforge-app
```

### 4. Verify health

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

### 5. Verify metrics

```bash
curl http://localhost:8080/api/metrics/summary
```

Example response:

```json
{
  "queuedJobs": 0,
  "runningJobs": 0,
  "scheduledJobs": 0,
  "succeededJobs": 4,
  "deadJobs": 1,
  "totalWorkers": 1,
  "idleWorkers": 1,
  "runningWorkers": 0,
  "redisStreamLength": 8
}
```

Prometheus-compatible metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Search for FlowForge metrics:

```bash
curl http://localhost:8080/actuator/prometheus | grep flowforge
```

On Windows PowerShell:

```powershell
curl http://localhost:8080/actuator/prometheus | Select-String flowforge
```

---

## Local Development Setup

If you want to run the Spring Boot app directly from your machine while still using Docker for PostgreSQL and Redis:

### 1. Start only dependencies

```bash
docker compose up -d postgres redis
```

### 2. Run the application locally

```bash
mvn spring-boot:run
```

Or package and run the JAR:

```bash
mvn clean package -DskipTests
java -jar target/flowforge-0.0.1-SNAPSHOT.jar
```

### 3. Test health

```bash
curl http://localhost:8080/actuator/health
```

> Note: Local execution uses `localhost:5433` for PostgreSQL and `localhost:6379` for Redis. Full Docker Compose execution uses Docker service names: `postgres:5432` and `redis:6379`.

---

## Configuration

The main configuration is in:

```text
src/main/resources/application.yml
```

FlowForge supports environment-variable-based configuration so the same application can run locally or inside Docker.

Example configuration:

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/flowforge}
    username: ${SPRING_DATASOURCE_USERNAME:flowforge}
    password: ${SPRING_DATASOURCE_PASSWORD:flowforge}

  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: flowforge

flowforge:
  redis:
    stream-key: ${FLOWFORGE_REDIS_STREAM_KEY:flowforge.jobs}
    group-name: ${FLOWFORGE_REDIS_GROUP_NAME:flowforge-workers}

  worker:
    enabled: ${FLOWFORGE_WORKER_ENABLED:true}
    poll-ms: ${FLOWFORGE_WORKER_POLL_MS:1000}
    heartbeat-ms: ${FLOWFORGE_WORKER_HEARTBEAT_MS:5000}

  recovery:
    scan-ms: ${FLOWFORGE_RECOVERY_SCAN_MS:5000}
    running-timeout-seconds: ${FLOWFORGE_RECOVERY_RUNNING_TIMEOUT_SECONDS:30}
    heartbeat-timeout-seconds: ${FLOWFORGE_RECOVERY_HEARTBEAT_TIMEOUT_SECONDS:15}
```

Docker Compose overrides local defaults using environment variables:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/flowforge
SPRING_DATA_REDIS_HOST: redis
```

---

## Dockerfile

FlowForge uses a multi-stage Docker build.

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build only the app image:

```bash
docker compose build app
```

Force a clean rebuild:

```bash
docker compose build --no-cache app
```

Run all services after rebuild:

```bash
docker compose up --build
```

---

## Docker Compose Services

FlowForge currently runs three services:

| Service | Container | Purpose | Host Port |
|---|---|---|---|
| `app` | `flowforge-app` | Spring Boot API, workers, schedulers, metrics | `8080` |
| `postgres` | `flowforge-postgres` | Job persistence and Flyway migrations | `5433` |
| `redis` | `flowforge-redis` | Redis Streams queue | `6379` |

PostgreSQL uses the internal container port `5432`, but it is exposed to the host as `5433` to avoid conflicts with local PostgreSQL installations.

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
    "workerId": "flowforge-app-36d6fd6d-0ef3-49c6-b2a4-1d0e624e8d8c",
    "hostname": "flowforge-app",
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

## Metrics Endpoints

### Health

```bash
curl http://localhost:8080/actuator/health
```

### Actuator metrics list

```bash
curl http://localhost:8080/actuator/metrics
```

### Prometheus metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

### FlowForge JSON metrics summary

```bash
curl http://localhost:8080/api/metrics/summary
```

Example:

```json
{
  "queuedJobs": 0,
  "runningJobs": 0,
  "scheduledJobs": 1,
  "succeededJobs": 12,
  "deadJobs": 2,
  "totalWorkers": 1,
  "idleWorkers": 1,
  "runningWorkers": 0,
  "redisStreamLength": 18
}
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

### Dockerized deployment

FlowForge uses Docker Compose to run the API/worker service, PostgreSQL, and Redis together on one isolated Docker network. The app container receives database and Redis connection settings through environment variables, making it portable across local and containerized environments.

### Metrics-first observability

FlowForge exposes both human-readable JSON metrics and Prometheus-compatible metrics. This makes it easier to inspect system health, queue state, worker activity, and job outcomes.

---

## Common Commands

### Run full Dockerized stack

```bash
docker compose up --build
```

Detached mode:

```bash
docker compose up --build -d
```

### Stop services

```bash
docker compose down
```

### Reset local database and Redis volume data

```bash
docker compose down -v
docker compose up --build
```

### View app logs

```bash
docker logs -f flowforge-app
```

### Rebuild only the app container

```bash
docker compose build app
```

Force clean rebuild:

```bash
docker compose build --no-cache app
```

### Run only dependencies for local development

```bash
docker compose up -d postgres redis
```

### Run app locally

```bash
mvn spring-boot:run
```

### Package executable JAR

```bash
mvn clean package -DskipTests
```

### Run executable JAR locally

```bash
java -jar target/flowforge-0.0.1-SNAPSHOT.jar
```

### Check Postgres tables

```bash
docker exec -it flowforge-postgres psql -U flowforge -d flowforge -c "\dt"
```

### Check Redis stream length

```bash
docker exec -it flowforge-redis redis-cli XLEN flowforge.jobs
```

### Inspect Redis stream

```bash
docker exec -it flowforge-redis redis-cli XRANGE flowforge.jobs - +
```

### Inspect Redis consumer group

```bash
docker exec -it flowforge-redis redis-cli XINFO GROUPS flowforge.jobs
```

---

## Troubleshooting

### `no main manifest attribute, in app.jar`

This means the JAR was not repackaged as an executable Spring Boot JAR. Make sure `pom.xml` includes:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Then rebuild:

```bash
mvn clean package -DskipTests
docker compose build --no-cache app
```

### `Connection to localhost:5433 refused`

This happens when running the app locally without PostgreSQL running. Start dependencies first:

```bash
docker compose up -d postgres redis
```

If running inside Docker Compose, the app should use:

```text
jdbc:postgresql://postgres:5432/flowforge
```

not:

```text
jdbc:postgresql://localhost:5433/flowforge
```

### Port `8080` already in use

Change the app port mapping in `docker-compose.yml`:

```yaml
ports:
  - "8081:8080"
```

Then use:

```bash
curl http://localhost:8081/actuator/health
```

### Local PostgreSQL conflicts with Docker PostgreSQL

This project maps Docker PostgreSQL to host port `5433`:

```yaml
ports:
  - "5433:5432"
```

If you prefer host port `5432`, update both `docker-compose.yml` and the local default datasource URL in `application.yml`.

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
- Dockerized Spring Boot application
- Docker Compose deployment for app, PostgreSQL, and Redis
- Environment-variable-based runtime configuration
- Actuator health endpoint
- Prometheus metrics endpoint
- Custom FlowForge metrics for jobs, workers, and Redis stream depth
- JSON metrics summary API

