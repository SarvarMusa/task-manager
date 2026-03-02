# Docker Containerization - Packaging the Application for Any Environment

## Date: 2026-03-02

---

## Problem

### The "It Works on My Machine" Disaster

Imagine this scenario. You develop the Task Manager on your laptop: Java 17, PostgreSQL 16, specific environment variables, specific file paths. 
Everything works perfectly. You hand it to your colleague. They have Java 11. They run `./mvnw spring-boot:run` and get:

```
Error: LinkageError occurred while loading main class org.task.taskmaganer.TaskManagerApplication
  java.lang.UnsupportedClassVersionError: org/task/taskmaganer/TaskManagerApplication 
  has been compiled by a more recent version of the Java Runtime (class file version 61.0)
```

OK, they install Java 17. Now PostgreSQL won't connect because they have MySQL installed, not PostgreSQL. 
They install PostgreSQL. Now the database name is wrong. They fix it. Now the port is 5433 instead of 5432. 
They fix it. Now Flyway migrations fail because the PostgreSQL version is 14, not 16, and there's a syntax incompatibility.

**Three hours later, they still can't run the application.**

Now multiply this by every new developer who joins the team, every staging server, every production deployment. 
Each one is a unique snowflake with its own Java version, its own database version, its own configuration quirks.

### What Specifically Breaks Without Containerization

**1. Java version mismatch:**
```
# Your laptop: Java 17 (required by Spring Boot 3.2)
# Production server: Java 11 (installed 2 years ago, nobody updated)
# Result: Application won't even start
```

**2. Database version mismatch:**
```
# Your laptop: PostgreSQL 16
# Staging server: PostgreSQL 13
# Result: Flyway migration V3 uses a JSON function that doesn't exist in PG 13
#         "ERROR: function jsonb_path_query does not exist"
```

**3. Missing environment variables:**
```
# Your laptop: SPRING_DATASOURCE_URL is in your .bashrc
# New developer: Has no idea this variable needs to be set
# Result: "Failed to configure a DataSource: 'url' attribute is not specified"
```

**4. Port conflicts:**
```
# Your laptop: Port 5432 is free
# Colleague's laptop: Port 5432 is used by their other project's PostgreSQL
# Result: "Address already in use: bind"
```

**5. OS-specific issues:**
```
# Your macOS: File paths are case-insensitive
# Linux production: File paths are case-sensitive
# Result: "FileNotFoundException: /app/Config/application.yml" 
#         (because the folder is actually "config" lowercase)
```

### The Core Problem in One Sentence

Every environment (your laptop, your colleague's laptop, staging, production) is **different**, 
and those differences cause failures that have **nothing to do with your actual code**.

---

## Solution

Docker solves this by packaging your application **with its entire environment** into a single, portable unit called a **container**. The container carries its own Java version, its own file system, its own network configuration. It runs identically everywhere Docker is installed.

Think of it like shipping. Before containers (the real, physical ones), cargo was loaded loose onto ships - fragile items broke, items got lost, loading took days. After standardized shipping containers were invented, you pack your goods into a container once, and it moves unchanged from factory to truck to ship to warehouse. Docker containers are the same idea applied to software.

We created three files:

| File | Purpose |
|------|---------|
| `Dockerfile` | The recipe for building our application image |
| `.dockerignore` | Tells Docker which files to skip (like `.gitignore` for Docker) |
| `docker-compose.yml` | Orchestrates our app + PostgreSQL together |

---

## Implementation

### Step 1: The Dockerfile - Building the Application Image

A Dockerfile is a set of instructions that Docker follows to build an **image**. An image is a snapshot of a complete environment - OS, Java, your JAR file, everything. You build the image once, then run it as many times as you want on any machine.

We use a technique called **multi-stage build**. This means the Dockerfile has two stages:

- **Stage 1 (builder):** Install the full JDK, download all Maven dependencies, compile the source code, produce the JAR file. This stage is heavy (~500MB) because it includes the compiler, Maven, source code, and all build tools.
- **Stage 2 (runtime):** Start fresh with a minimal image that has only the JRE (not the full JDK), copy the JAR from Stage 1, and run it. This stage is light (~200MB) because it has no compiler, no Maven, no source code.

**Why two stages?** The same reason you don't ship the entire factory along with the product. The customer only needs the finished product, not the assembly line.

```dockerfile
# File: Dockerfile

# ======================== STAGE 1: BUILD ========================
# eclipse-temurin = Official Eclipse Foundation Java distribution
# 17-jdk = Java 17, full JDK (includes compiler, needed to build)
# alpine = Minimal Linux distribution (~5MB vs ~80MB for Ubuntu)
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml FIRST (before source code)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all dependencies. This is a SEPARATE step from copying source code.
# WHY? Docker caches each step. If your source code changes but pom.xml doesn't,
# Docker reuses the cached dependencies instead of downloading 200MB again.
# This turns a 3-minute build into a 10-second build.
RUN ./mvnw dependency:go-offline -B

# NOW copy the source code (this step changes frequently)
COPY src src

# Build the JAR. -DskipTests because tests run in CI/CD, not during image build.
# -B = batch mode (no interactive prompts, cleaner output)
RUN ./mvnw clean package -DskipTests -B


# ======================== STAGE 2: RUNTIME ========================
# 17-jre = Java 17, JRE only (no compiler, smaller image)
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: Create a non-root user. By default, Docker runs as root.
# If an attacker exploits a vulnerability in your app, they get root access
# to the container. With a non-root user, the damage is limited.
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -s /bin/sh -D appuser

WORKDIR /app

# Copy ONLY the JAR from Stage 1. Everything else (source code, Maven,
# downloaded dependencies, .class files) is thrown away.
COPY --from=builder /build/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

# Switch to non-root user for all subsequent commands
USER appuser

# Document which port the app uses (doesn't actually open the port)
EXPOSE 8080

# Docker health check: every 30 seconds, curl the health endpoint.
# If it fails 3 times in a row, Docker marks the container as "unhealthy".
# start-period=60s gives the app time to boot before checking.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# ENTRYPOINT vs CMD:
# ENTRYPOINT = "this container IS a Java application" (always runs java -jar)
# CMD = "default arguments that can be overridden"
# We use ENTRYPOINT because this container has one purpose: run our app.
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Why Each Decision Matters

**Why `eclipse-temurin` and not `openjdk`?**

The `openjdk` Docker images are deprecated. Eclipse Temurin is the official successor, maintained by the Eclipse Adoptium project. It's the same OpenJDK code, just properly packaged and supported.

**Why `alpine` and not `ubuntu`?**

| Base Image | Size | Packages |
|-----------|------|----------|
| `eclipse-temurin:17-jdk-alpine` | ~340MB | Minimal (just what's needed) |
| `eclipse-temurin:17-jdk` (Ubuntu) | ~470MB | Lots of extras you don't need |

Alpine is smaller because it uses `musl libc` instead of `glibc` and includes only the bare minimum. Smaller image = faster downloads, faster deployments, smaller attack surface.

**Why copy `pom.xml` before `src`?**

Docker builds images in layers. Each instruction (`COPY`, `RUN`) creates a layer. Docker caches layers and reuses them if the input hasn't changed. By copying `pom.xml` first and running `dependency:go-offline`, we create a cached layer of all dependencies. When you change a Java file in `src/`, Docker doesn't re-download dependencies — it only rebuilds the `COPY src` and `RUN mvnw package` layers. This is the difference between a 3-minute build and a 10-second build.

```
Layer 1: COPY pom.xml          → cached (pom.xml didn't change)
Layer 2: RUN dependency:go-offline → cached (same pom.xml = same dependencies)
Layer 3: COPY src               → REBUILT (you changed TaskService.java)
Layer 4: RUN mvnw package       → REBUILT (source changed, need to recompile)
```

**Why `ENTRYPOINT ["java", "-jar", "app.jar"]` and not `CMD`?**

`ENTRYPOINT` defines what this container **is**. It's a Java application. Period. You can't accidentally override it with `docker run task-manager /bin/bash` — the container will always run the JAR. `CMD` would allow overriding, which makes sense for general-purpose images but not for an application container.

---

### Step 2: The .dockerignore File

When you run `docker build`, Docker sends your entire project directory to the Docker daemon as a "build context." Without `.dockerignore`, it sends everything — `.git` (which can be hundreds of MB), `target/` (compiled classes you'll rebuild anyway), IDE files, logs, documentation.

```
# File: .dockerignore

# Git history - can be 100MB+ and is never needed in the image
.git
.gitignore
.gitattributes

# IDE files - IntelliJ, VS Code, Eclipse
.idea
.vscode
*.iml
*.ipr
*.iws
.classpath
.project
.settings

# Build output - we build fresh inside the container
target
!target/*.jar

# Logs - old log files don't belong in an image
logs
*.log

# OS files
.DS_Store
Thumbs.db

# Docker files themselves - no recursion needed
Dockerfile
docker-compose.yml
.dockerignore

# Documentation - not needed at runtime
docs
task-manager-documents
*.md

# Shell scripts - development utilities
*.sh

# Temporary files
*.tmp
*.swp
*~
```

**Why this matters:**

Without `.dockerignore`, `docker build` sends ~150MB of context (including `.git/` and `target/`). With `.dockerignore`, it sends ~50KB (just `pom.xml`, `mvnw`, `.mvn/`, and `src/`). The build is faster and the Docker cache is invalidated less often.

The line `target` with `!target/*.jar` means "ignore the entire `target/` directory, but NOT the JAR files inside it." In practice, the JAR is built inside the container anyway, so this is a safety measure.

---

### Step 3: Docker Compose - Orchestrating Multiple Services

Our application needs two things to run: itself and a PostgreSQL database. Without Docker Compose, you would start each one manually:

```bash
# Start PostgreSQL manually
docker run -d --name postgres \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=task_manager \
  -p 5432:5432 \
  postgres:16-alpine

# Wait for PostgreSQL to be ready (how long? who knows?)
sleep 10  # hope this is enough...

# Build and start the app manually
docker build -t task-manager .
docker run -d --name app \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/task_manager \
  -p 8080:8080 \
  --link postgres \
  task-manager
```

This is tedious, error-prone, and doesn't handle startup ordering. Docker Compose replaces all of this with a single YAML file and one command: `docker compose up`.

```yaml
# File: docker-compose.yml

services:

  # ======================== DATABASE ========================
  postgres_db:
    image: postgres:16-alpine
    container_name: task_manager_db
    restart: always
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password
      POSTGRES_DB: task_manager
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d task_manager"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - task_manager_network


  # ======================== APPLICATION ========================
  task_manager_app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: task_manager_app
    restart: always
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres_db:5432/task_manager
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: password
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.postgresql.Driver
      SPRING_JPA_HIBERNATE_DDL_AUTO: validate
      SPRING_JPA_SHOW_SQL: "false"
      SPRING_FLYWAY_ENABLED: "true"
      SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
      JAVA_OPTS: "-Xms512m -Xmx1024m -XX:+UseG1GC"
    ports:
      - "8080:8080"
    depends_on:
      postgres_db:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
             "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - task_manager_network


volumes:
  postgres_data:
    driver: local

networks:
  task_manager_network:
    driver: bridge
```

Let's go through every section and explain **why** it's there.

### PostgreSQL Service - Line by Line

**`image: postgres:16-alpine`**

We pin the exact major version (16) instead of using `latest`. Why? Because `latest` changes over time. Today it's PostgreSQL 16, next month it might be 17. If PostgreSQL 17 introduces a breaking change to a function our Flyway migration uses, your production deployment breaks on a random Tuesday because someone ran `docker compose pull`. Pinning the version means you upgrade **deliberately**, not accidentally.

Alpine variant because it's smaller (~80MB vs ~420MB for the full Debian image) and has a smaller attack surface.

**`restart: always`**

If the container crashes (out-of-memory, kernel bug, segfault), Docker automatically restarts it. Without this, a database crash at 3 AM means your app is down until someone manually restarts it in the morning.

**`environment: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB`**

These are special environment variables that the PostgreSQL Docker image reads on first startup. It creates the database and user automatically. Without them, you'd have to connect to the container and run `CREATE DATABASE` and `CREATE USER` manually.

**`volumes: - postgres_data:/var/lib/postgresql/data`**

This is critical. Without a volume, all database data lives inside the container's filesystem. When you run `docker compose down`, the container is deleted, and **all your data is gone**. A named volume (`postgres_data`) stores data outside the container. The container can be destroyed and recreated, but the data persists.

A common mistake (that we actually hit during setup) is mounting to `/var/lib/postgresql` instead of `/var/lib/postgresql/data`. PostgreSQL expects the data directory to be **exactly** `/var/lib/postgresql/data`. If you mount a volume to the parent directory, the container puts its own files there, and on restart, PostgreSQL sees a non-empty directory that isn't a valid data directory:

```
initdb: error: directory "/var/lib/postgresql/data" exists but is not empty
```

**`healthcheck`**

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U admin -d task_manager"]
  interval: 10s
  timeout: 5s
  retries: 5
```

`pg_isready` is a PostgreSQL utility that checks if the database is accepting connections. Docker runs this command every 10 seconds. If it fails 5 times in a row (with a 5-second timeout each), Docker marks the container as "unhealthy." This health check is what the application service uses in `depends_on: condition: service_healthy` - the app won't start until PostgreSQL is truly ready, not just "container started."

Without the health check, `depends_on` only waits for the container to start, not for PostgreSQL to be ready. PostgreSQL takes a few seconds to initialize, and the app might try to connect before the database is ready:

```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

**`networks: - task_manager_network`**

A custom bridge network isolates our services from other Docker containers on the same host. Containers on the same network can talk to each other by service name (e.g., `postgres_db`). Containers on different networks can't see each other at all. This is basic network security.

### Application Service - Line by Line

**`build: context: . dockerfile: Dockerfile`**

Tells Docker Compose to build the image from our Dockerfile instead of pulling a pre-built image from Docker Hub. The `.` context means "use the current directory as the build context" (what gets sent to the Docker daemon, filtered by `.dockerignore`).

**`environment: SPRING_DATASOURCE_URL: jdbc:postgresql://postgres_db:5432/task_manager`**

Notice the hostname is `postgres_db`, not `localhost`. Inside Docker's network, containers refer to each other by their **service name**. `postgres_db` is the name we gave the database service in this same compose file. Docker's internal DNS resolves `postgres_db` to the container's IP address automatically.

Spring Boot has a property resolution hierarchy:
1. `application.yml` (lowest priority)
2. `application-prod.yml` (profile-specific)
3. **Environment variables** (highest priority)

Environment variables like `SPRING_DATASOURCE_URL` override the `spring.datasource.url` property from `application-prod.yml`. Spring automatically converts `SPRING_DATASOURCE_URL` (uppercase with underscores) to `spring.datasource.url` (lowercase with dots). This means we can change the database host, credentials, or any Spring property without rebuilding the Docker image — just change the environment variable and restart.

**`depends_on: postgres_db: condition: service_healthy`**

This tells Docker Compose: "Do NOT start the application until the `postgres_db` service passes its health check." Without `condition: service_healthy`, Docker would start both containers simultaneously, and the app would crash because PostgreSQL isn't ready yet.

The startup sequence becomes:
1. Docker starts `postgres_db`
2. Docker waits, checking `pg_isready` every 10 seconds
3. After 2-3 checks (~20-30 seconds), PostgreSQL is healthy
4. Docker starts `task_manager_app`
5. Spring Boot connects to PostgreSQL, Flyway runs migrations, app starts

**`healthcheck` for the application**

```yaml
healthcheck:
  test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
         "http://localhost:8080/actuator/health"]
  start_period: 60s
```

The `start_period: 60s` is important. Spring Boot takes 30-60 seconds to start (dependency injection, component scanning, Flyway migrations, JPA initialization). Without a start period, Docker would check health immediately, find the app not responding, and mark it as unhealthy before it even finishes booting.

We use `wget` instead of `curl` because the Alpine-based image has `wget` built in but not `curl`. The `--spider` flag means "check if the URL exists without downloading the content."

This health check calls `/actuator/health`, which is provided by Spring Boot Actuator (see `10-spring-boot-actuator.md` for details on why we added that dependency).

### Volumes and Networks

```yaml
volumes:
  postgres_data:
    driver: local

networks:
  task_manager_network:
    driver: bridge
```

**`volumes: postgres_data: driver: local`** declares a named volume stored on the host machine's filesystem. Docker manages where it's stored (typically `/var/lib/docker/volumes/`). Named volumes persist across `docker compose down` — only `docker compose down -v` (with the `-v` flag) deletes them.

**`networks: task_manager_network: driver: bridge`** creates an isolated virtual network. "Bridge" is Docker's default network driver — it creates a private network where containers can communicate with each other using service names as hostnames, and with the outside world through port mappings.

---

## What We Gained

### 1. One Command to Run Everything

**Before:**
```bash
# Install Java 17
# Install PostgreSQL 16
# Create database "task_manager"
# Create user "admin" with password "password"
# Set environment variables
# Run Flyway migrations
# Start the application
# Hope nothing conflicts with your existing PostgreSQL installation
```

**After:**
```bash
docker compose up --build
# That's it. Java, PostgreSQL, database, migrations, app — all started and connected.
```

A new developer clones the repo and runs one command. No Java installation, no PostgreSQL installation, no environment variables to configure. Everything is defined in the compose file.

### 2. Identical Environments

The Docker image is the same binary artifact everywhere. Your laptop, the CI/CD pipeline, the staging server, and the production server all run the exact same image with the exact same Java version, the exact same Alpine Linux, the exact same JAR file. The "it works on my machine" problem is eliminated by definition.

### 3. Isolation

Our PostgreSQL runs on port 5432 inside the Docker network. If your colleague already has PostgreSQL on port 5432, there's no conflict — Docker's internal network is separate. You could even change the host port mapping to `"5433:5432"` and the app still connects on port 5432 internally.

### 4. Reproducible Builds

The Dockerfile is version-controlled in Git alongside the source code. If something breaks in production, you can check out the exact commit, build the exact same image, and reproduce the problem. No guessing which Java version was installed on the production server six months ago.

### 5. Easy Cleanup

```bash
docker compose down     # Stop everything, remove containers
docker compose down -v  # Stop everything, remove containers AND data
```

No leftover PostgreSQL processes, no orphaned database files, no "I can't uninstall Java because another app depends on it."

---

## Advantages

| Advantage | Explanation |
|-----------|-------------|
| **One-command startup** | `docker compose up` starts everything — no manual installation or configuration |
| **Environment consistency** | Same image runs on every machine — Java version, OS, and dependencies are locked |
| **Network isolation** | Containers communicate internally; no port conflicts with host services |
| **Persistent data** | Named volumes survive container restarts and recreations |
| **Health checks** | Docker verifies services are truly ready, not just "process started" |
| **Startup ordering** | `depends_on` + health checks ensure PostgreSQL is ready before the app starts |
| **Reproducibility** | `Dockerfile` and `docker-compose.yml` are version-controlled — rebuild any version at any time |
| **Security** | Non-root user, minimal Alpine image, network isolation |
| **Fast onboarding** | New developer: clone repo, run `docker compose up`, done |
| **CI/CD ready** | Same compose file works in Jenkins, GitHub Actions, GitLab CI |

## Disadvantages

| Disadvantage | Explanation | Mitigation |
|--------------|-------------|------------|
| **Docker required** | Every developer must install Docker Desktop or Docker Engine | One-time installation; available for Windows, macOS, Linux |
| **Build time** | First build downloads all Maven dependencies (~3 minutes) | Subsequent builds use cached layers (~10 seconds) |
| **Resource overhead** | Docker daemon uses some CPU and memory | Negligible on modern hardware; much less than running a VM |
| **Debugging complexity** | Can't just attach IntelliJ debugger to a container | Use `docker compose logs -f` or configure remote debugging on port 5005 |
| **Volume confusion** | Stale volumes can cause cryptic errors | `docker compose down -v` clears everything for a fresh start |
| **Learning curve** | Team must understand Docker basics | This document covers everything needed |

---

## Common Commands

```bash
# Build and start all services (foreground - see logs directly)
docker compose up --build

# Build and start in background (detached mode)
docker compose up -d --build

# View logs (follow mode - like tail -f)
docker compose logs -f

# View logs for one service only
docker compose logs -f task_manager_app

# Check health status of all containers
docker compose ps

# Stop all services (keeps volumes and images)
docker compose down

# Stop all services AND delete database data (fresh start)
docker compose down -v

# Rebuild without cache (when something is stuck)
docker compose build --no-cache
docker compose up -d

# Enter a running container for debugging
docker exec -it task_manager_app sh
docker exec -it task_manager_db psql -U admin -d task_manager

# Check resource usage
docker stats
```

---

## Troubleshooting

### "directory /var/lib/postgresql/data exists but is not empty"

**Cause:** Volume mounted to wrong path, or stale data from a previous failed setup.

**Fix:**
```bash
docker compose down -v   # -v removes the volume
docker compose up --build
```

### "Connection to postgres_db:5432 refused"

**Cause:** App started before PostgreSQL was ready.

**Fix:** This should not happen with our `depends_on: condition: service_healthy` configuration. If it does, check that the `healthcheck` section exists in the database service.

### "Port 5432 already in use"

**Cause:** Another PostgreSQL instance (or another Docker container) is using port 5432 on your host.

**Fix:** Change the host port mapping:
```yaml
ports:
  - "5433:5432"  # Host port 5433 maps to container port 5432
```
The app still connects on port 5432 **inside the Docker network** — the host port mapping only affects external access.

### Changes to Java code aren't reflected

**Cause:** Docker is using a cached image layer.

**Fix:**
```bash
docker compose build --no-cache
docker compose up -d
```

### "No space left on device"

**Cause:** Docker images and volumes accumulate over time.

**Fix:**
```bash
docker system prune -a  # Remove unused images, containers, networks
docker volume prune      # Remove unused volumes
```

---

## Affected Files

- [x] `Dockerfile` — **New**: Multi-stage build with JDK (builder) and JRE (runtime)
- [x] `.dockerignore` — **New**: Excludes .git, IDE files, target/, logs, docs from build context
- [x] `docker-compose.yml` — **Updated**: Added app service, health checks, custom network, pinned PostgreSQL version
- [x] `pom.xml` — **Updated**: Added `spring-boot-starter-actuator` dependency (required for health check endpoint)
