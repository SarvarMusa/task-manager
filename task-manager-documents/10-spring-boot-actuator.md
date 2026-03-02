# Spring Boot Actuator - Why the Application Needed a Health Endpoint

## Date: 2026-03-02

---

## Problem

### Docker Started the Container, But Had No Way to Know If the App Was Actually Working

When we first set up Docker (see `09-docker-containerization.md`), we wrote this health check in `docker-compose.yml`:

```yaml
task_manager_app:
  healthcheck:
    test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
           "http://localhost:8080/actuator/health"]
```

Docker ran `wget` against `http://localhost:8080/actuator/health` every 30 seconds. The app responded with this:

```
NoResourceFoundException: No static resource actuator/health.
```

Docker saw the request fail, marked the container as **unhealthy**,
and refused to keep it running. The container kept restarting in an infinite loop:

```
Container task_manager_app  Error
dependency failed to start: container task_manager_db is unhealthy
```

The application was actually working fine — you could call `/api/v1/tasks` and get a response.
But the specific URL that Docker was checking (`/actuator/health`) didn't exist.
It's like a security guard checking if a building is open by trying door #7, but door #7 doesn't exist.
The building is open — just not through that door.

### Why Not Just Change the Health Check URL?

The obvious thought is: "Why not point the health check at `/api/v1/tasks` or `/swagger-ui.html` instead?"

We could. But those endpoints have problems as health indicators:

**1. `/api/v1/tasks` requires authentication (Spring Security):**
```bash
wget http://localhost:8080/api/v1/tasks
# Returns 401 Unauthorized — Docker thinks the app is unhealthy
```

**2. `/swagger-ui.html` returns a redirect (302), not a 200:**
```bash
wget --spider http://localhost:8080/swagger-ui.html
# Returns 302 redirect to /swagger-ui/index.html
# wget --spider treats redirects differently per version — unreliable
```

**3. Neither endpoint checks database connectivity:**

Your API endpoint might return 200 even when the database is down — maybe it's serving a cached response, or the endpoint doesn't hit the database at all. A health check should verify that the application **and its dependencies** (database, cache, external services) are working.

### What We Actually Needed

A dedicated endpoint that:
- Returns `200 OK` when the application is healthy
- Returns `503 Service Unavailable` when something is broken
- Checks the database connection (not just the web server)
- Requires no authentication (Docker can't send auth headers)
- Is a standard that every tool understands (Docker, Kubernetes, load balancers)

This is exactly what Spring Boot Actuator provides.

---

## Solution

We added one dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

No Java code changes. No configuration files. No new classes. Just this one dependency.

After adding it and rebuilding the Docker image, the health check started working:

```bash
curl http://localhost:8080/actuator/health
{"status":"UP"}
```

Docker sees `HTTP 200` with `"status":"UP"` and marks the container as healthy. The restart loop stopped. Everything works.

---

## What Spring Boot Actuator Actually Does

### The `/actuator/health` Endpoint

This is the primary reason we added Actuator. When you call this endpoint, Spring Boot doesn't just return a static "I'm alive" response. It actually runs checks:

1. **Is the application context running?** (Can Spring process requests?)
2. **Can the app connect to the database?** (Is PostgreSQL reachable and accepting queries?)
3. **Is there enough disk space?** (Is the container's filesystem full?)

If all checks pass:
```json
{"status": "UP"}
```

If any check fails (e.g., database is down):
```json
{"status": "DOWN"}
```

When the status is `DOWN`, the HTTP response code is `503 Service Unavailable`, not `200 OK`. This means Docker's health check (`wget --spider`) will fail, Docker marks the container as unhealthy, and if `restart: always` is set, Docker restarts it.

**This is the behavior we want.** If the database goes down, we want Docker to know the app is unhealthy. Without Actuator, Docker would think the app is fine (because the Java process is still running), and users would get `500 Internal Server Error` on every request with no automatic recovery.

### How Docker Uses the Health Endpoint

Here's the timeline of what happens with our configuration:

```yaml
healthcheck:
  test: ["CMD", "wget", "--quiet", "--tries=1", "--spider",
         "http://localhost:8080/actuator/health"]
  interval: 30s       # Check every 30 seconds
  timeout: 10s        # Wait up to 10 seconds for a response
  retries: 3          # Allow 3 consecutive failures before marking unhealthy
  start_period: 60s   # Don't check for the first 60 seconds (app is booting)
```

```
t=0s     Docker starts the container
t=0-60s  start_period: Docker does NOT check health (Spring Boot is still booting)
t=60s    First health check: wget http://localhost:8080/actuator/health → 200 OK
t=90s    Second health check: 200 OK
t=120s   Third health check: 200 OK
...
t=300s   Database goes down
t=330s   Health check: wget → 503 Service Unavailable (failure 1 of 3)
t=360s   Health check: wget → 503 (failure 2 of 3)
t=390s   Health check: wget → 503 (failure 3 of 3)
t=390s   Docker marks container as UNHEALTHY
t=390s   restart: always → Docker restarts the container
t=450s   Container reboots, database is back → 200 OK
t=450s   Docker marks container as HEALTHY
```

Without Actuator, the health check at t=330s would get a `404 Not Found` (endpoint doesn't exist), and Docker couldn't distinguish between "app is broken" and "app has no health endpoint."

### Other Endpoints Actuator Provides

Actuator comes with more than just `/health`. These are disabled by default but can be enabled:

```
/actuator/health    → Application health status (enabled by default)
/actuator/info      → Application metadata (version, git commit, etc.)
/actuator/metrics   → JVM metrics (memory usage, CPU, thread count, HTTP request stats)
/actuator/env       → All Spring properties and environment variables
/actuator/loggers   → View and change log levels at runtime (without restart!)
/actuator/threaddump → Thread dump for debugging deadlocks
/actuator/heapdump  → JVM heap dump for memory analysis
```

We only use `/health` right now. The others become valuable when you set up monitoring with tools like Prometheus, Grafana, or Datadog.

---

## Why Not Write Our Own Health Endpoint?

You might think: "Why add a whole dependency for one endpoint? I can write it myself in 5 lines:"

```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
```

This looks simple, but it has serious problems:

**1. It doesn't check the database.**

Your custom `/health` endpoint returns `"UP"` even when PostgreSQL is completely down. The Java process is running, the web server is responding, but the application is useless because it can't read or write data. Actuator's health endpoint automatically includes a `DataSourceHealthIndicator` that runs `SELECT 1` against the database. If that fails, the status becomes `DOWN`.

**2. It doesn't check disk space.**

If the container's filesystem is full (logs filled it up), your app can't write temp files, logs, or uploaded content. Actuator checks this automatically.

**3. It requires Spring Security configuration.**

Our app uses `spring-boot-starter-security`. By default, every endpoint requires authentication. So your custom `/health` endpoint returns `401 Unauthorized` to Docker's `wget`. You'd need to add a security exception:

```java
// You'd need to write this
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/health").permitAll()
    .anyRequest().authenticated()
);
```

Actuator endpoints are automatically excluded from Spring Security in Spring Boot 3.x. You get this for free.

**4. It's not a standard.**

Kubernetes, Docker, AWS ELB, Azure App Service, NGINX, and every other infrastructure tool understands `/actuator/health`. They know that `200 OK` means healthy and `503` means unhealthy. If you use `/health` or `/api/ping` or `/status`, you need to document and configure this for every tool individually.

**5. You miss out on extensibility.**

Actuator lets you add custom health indicators that plug into the same system:

```java
@Component
public class ExternalServiceHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check if the external payment service is reachable
        boolean isReachable = pingPaymentService();
        if (isReachable) {
            return Health.up().withDetail("paymentService", "reachable").build();
        }
        return Health.down().withDetail("paymentService", "unreachable").build();
    }
}
```

Now `/actuator/health` automatically includes your custom check alongside the database and disk space checks. With a custom endpoint, you'd have to wire all of this yourself.

---

## The Actuator Dependency in Context

Here is where Actuator fits in our `pom.xml` relative to the other dependencies:

```xml
<!-- Web framework (handles HTTP requests) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Database access (JPA/Hibernate) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Authentication and authorization -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Health checks and monitoring (NEW) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Every Spring Boot starter follows the same pattern: add the dependency, and Spring Boot auto-configures everything. No `@Bean` definitions, no XML configuration, no manual endpoint registration. Actuator detects that you have `spring-boot-starter-data-jpa` in your classpath and automatically adds a database health check. It detects `spring-boot-starter-web` and exposes the health endpoint over HTTP. This is Spring Boot's "convention over configuration" philosophy — sensible defaults that work out of the box.

---

## Security Considerations

### What Actuator Exposes by Default

In Spring Boot 3.x, only `/actuator/health` is exposed over HTTP by default. All other endpoints (`/env`, `/metrics`, `/loggers`, etc.) are **disabled** by default. This is the secure default — you opt in to each endpoint explicitly.

If you later need metrics for Prometheus:
```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus  # Only expose what you need
  endpoint:
    health:
      show-details: never  # Don't show database connection details to the public
```

`show-details: never` means the health endpoint returns just `{"status":"UP"}` without listing which components were checked. In production, you don't want to expose that you're running PostgreSQL 16 on a specific host — that's information an attacker could use.

### Spring Security Integration

Because our app uses `spring-boot-starter-security`, all regular endpoints require authentication. But Actuator's `/actuator/health` is automatically permitted without authentication in Spring Boot 3.x. This is essential because Docker's health check can't send authentication headers.

If you wanted to restrict access to other actuator endpoints (like `/actuator/env` which shows all environment variables including database passwords), you would configure Spring Security:

```java
// Only needed if you enable sensitive actuator endpoints
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()     // Docker needs this
    .requestMatchers("/actuator/info").permitAll()        // Public metadata
    .requestMatchers("/actuator/**").hasRole("ADMIN")     // Everything else: admin only
    .anyRequest().authenticated()
);
```

---

## Advantages

| Advantage | Explanation |
|-----------|-------------|
| **Zero code required** | Add one dependency, get a working health endpoint. No `@Controller`, no `@Bean`, no configuration. |
| **Database-aware** | Automatically checks PostgreSQL connectivity, not just "is the JVM alive" |
| **Industry standard** | Docker, Kubernetes, AWS, NGINX all understand `/actuator/health` natively |
| **Secure by default** | Only `/health` is exposed; sensitive endpoints are disabled |
| **Extensible** | Add custom health indicators for any external dependency (Redis, S3, email server) |
| **Spring Security compatible** | Health endpoint works without auth; no special configuration needed |
| **Auto-configured** | Detects your database, cache, and messaging dependencies and adds health checks for each |

## Disadvantages

| Disadvantage | Explanation | Mitigation |
|--------------|-------------|------------|
| **Additional dependency** | Adds ~2MB to the JAR file | Negligible compared to the 50MB+ JAR |
| **Potential information leak** | If misconfigured, could expose environment variables or database details | Default config only exposes `/health` with `show-details: never` |
| **Overhead** | Health checks run `SELECT 1` against the database periodically | One trivial query every 30 seconds — unmeasurable overhead |

---

## Affected Files

- [x] `pom.xml` — **Updated**: Added `spring-boot-starter-actuator` dependency
- [x] `docker-compose.yml` — Health check in the application service now works because `/actuator/health` exists
- [x] `Dockerfile` — `HEALTHCHECK` instruction calls `/actuator/health`
