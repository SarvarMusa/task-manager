# Logging Guide - From Basics to Advanced

## Date: 2026-02-27

---

## What is Logging and Why Does It Matter?

**Logging is like a security camera for your application.** Without cameras, when something goes wrong in a building, you have NO idea what happened - was it a break-in at 2 AM or a false alarm? With cameras (logs), you can rewind and see exactly what happened, when it happened, and why.

Now imagine debugging a production issue without logs:

> "The user says task creation is broken."  
> "When did it break?"  
> "Sometime today."  
> "What error did they get?"  
> "They don't remember."  
> "Can we check what happened on the server?"  
> "We have no logs."  
> "..."

With proper logging, the conversation becomes:

> "The user says task creation is broken. Their correlation ID is `a3f8b2c1`."  
> *grep logs*  
> "Found it. At 14:32:07, the database rejected a null `title` field. The validation was bypassed because of a missing `@Valid` annotation on the batch endpoint."  
> "Fix deployed in 10 minutes."

### Why Logging Matters - At a Glance

| Reason | What It Means | Real Example |
|--------|---------------|--------------|
| **Debugging** | Find the root cause of bugs | "Why did task #42 fail to save?" - check the logs, see the SQL error |
| **Monitoring** | Know your app's health in real-time | "Are there more errors than usual today?" - count ERROR lines |
| **Audit Trail** | Track who did what and when | "Who deleted the admin account?" - check audit logs |
| **Security** | Detect suspicious activity | "5 failed logins from same IP in 10 seconds" - possible brute force |
| **Error Tracking** | Find and fix errors before users report them | "NullPointerException at TaskService.java:42" - fix it proactively |
| **Performance** | Find slow operations | "GET /api/tasks took 8500ms" - optimize that database query |

---

## Log Levels

**Think of log levels like a volume knob on a radio:**
- **DEBUG** = Hear everything, including whispers and background noise
- **INFO** = Normal conversation volume - the important stuff
- **WARN** = Someone raised their voice - pay attention
- **ERROR** = Someone is screaming - something is broken

The levels form a hierarchy: `DEBUG < INFO < WARN < ERROR`

When you set the level to `INFO`, you see `INFO + WARN + ERROR` (but not `DEBUG`). When you set it to `ERROR`, you only see `ERROR`. This is how you control the "noise level."

### DEBUG - The Detective Level

**When to use:** You're investigating a problem and need to see every detail. Like dusting for fingerprints at a crime scene.

```java
log.debug("Looking up task in database | taskId={}", taskId);
log.debug("Found task | taskId={} | title={} | status={}", taskId, task.getTitle(), task.getStatus());
log.debug("Checking if user {} has permission to edit task {}", userId, taskId);
```

**Real example output:**
```
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] TaskService - Looking up task in database | taskId=550e8400-...
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] TaskService - Found task | taskId=550e8400-... | title=Fix login bug | status=IN_PROGRESS
2026-02-27 10:30:01 DEBUG [http-nio-8080-exec-1] TaskService - Checking if user admin has permission to edit task 550e8400-...
```

**Rule of thumb:** DEBUG is **OFF in production** (too noisy, too much disk space). Only enable when actively investigating.

### INFO - The Narrator Level

**When to use:** Important business events that you want to track normally. Like a narrator telling the story of your app's day.

```java
log.info("Task created successfully | taskId={} | createdBy={}", task.getId(), username);
log.info("User logged in | username={} | ip={}", username, ipAddress);
log.info("Application started on port {}", port);
log.info("Database migration completed | version={} | duration={}ms", version, duration);
```

**Real example output:**
```
2026-02-27 10:30:00 INFO  [main] Application - Application started on port 8080
2026-02-27 10:30:15 INFO  [http-nio-8080-exec-1] AuthService - User logged in | username=john_doe | ip=192.168.1.100
2026-02-27 10:30:20 INFO  [http-nio-8080-exec-1] TaskService - Task created successfully | taskId=550e8400-... | createdBy=john_doe
```

**Rule of thumb:** INFO is the **default production level**. These logs tell you the story of what your app is doing without overwhelming detail.

### WARN - The Yellow Flag Level

**When to use:** Something unexpected happened, but the application can continue. Like a "Check Engine" light - not an emergency, but you should investigate soon.

```java
log.warn("Slow database query detected | operation=findAllTasks | duration={}ms", duration);
log.warn("Retry attempt {} of {} for external service call | service={}", attempt, maxRetries, serviceName);
log.warn("User login failed | username={} | ip={} | reason=wrong_password", username, ip);
log.warn("Cache miss for frequently accessed data | key={}", cacheKey);
```

**Real example output:**
```
2026-02-27 10:30:00 WARN  [http-nio-8080-exec-3] TaskService - Slow database query detected | operation=findAllTasks | duration=3200ms
2026-02-27 10:30:05 WARN  [http-nio-8080-exec-1] AuthService - User login failed | username=admin | ip=45.33.32.156 | reason=wrong_password
```

**Rule of thumb:** If you see a WARN, it's not urgent, but if you see many WARNs of the same type, something is degrading.

### ERROR - The Red Alert Level

**When to use:** Something broke and a specific operation failed. Like a fire alarm - someone needs to look at this NOW.

```java
log.error("Failed to save task to database | taskId={} | error={}", taskId, ex.getMessage(), ex);
log.error("External payment service unavailable | url={} | timeout={}ms", url, timeout, ex);
log.error("Database connection pool exhausted | activeConnections={} | maxPool={}", active, max, ex);
```

**Real example output:**
```
2026-02-27 10:30:00 ERROR [http-nio-8080-exec-1] TaskService - Failed to save task to database | taskId=550e8400-... | error=Connection refused
    org.springframework.dao.DataAccessException: Connection refused
        at org.hibernate.internal.SessionImpl.save(SessionImpl.java:342)
        at org.task.taskmaganer.service.TaskService.createTask(TaskService.java:45)
        ...
```

**Rule of thumb:** ERROR should always include the exception object (`ex` as the last parameter) so the full stack trace is logged. Every ERROR should trigger an alert in production.

---

## Logging Architecture

### 1. SLF4J + Logback

Our project uses **SLF4J** (Simple Logging Facade for Java) as the logging API and **Logback** as the implementation.

**Why SLF4J and not `System.out.println`?**

```java
// TERRIBLE - never do this
System.out.println("Task created: " + task.getId());
// Problems:
// - No log level (is this debug info or critical?)
// - No timestamp (when did this happen?)
// - No class name (where did this come from?)
// - No file output (only goes to console)
// - String concatenation always happens, even if nobody reads it

// GOOD - always do this
private static final Logger log = LoggerFactory.getLogger(TaskService.class);
log.info("Task created | taskId={}", task.getId());
// Benefits:
// - Log level (INFO)
// - Automatic timestamp
// - Automatic class name (TaskService)
// - Configurable output (console, file, both)
// - {} placeholder: string is built ONLY if INFO level is enabled
```

**The `{}` placeholder - why it matters for performance:**

```java
// BAD: String concatenation always happens (even when DEBUG is disabled)
log.debug("Processing task: " + taskId + " with title: " + title + " and status: " + status);
// Even if DEBUG is OFF, Java still builds the entire string. Wasted CPU + memory.

// GOOD: {} placeholder - string built only when needed
log.debug("Processing task: {} with title: {} and status: {}", taskId, title, status);
// If DEBUG is OFF, the string is never built. Zero overhead.
```

**Why SLF4J (facade) and not Logback directly?**

SLF4J is a **facade** (a common interface). Think of it like a universal power adapter:

```
Your Code → SLF4J (universal interface) → Logback (actual implementation)
                                        → Log4j2 (can swap without code changes)
                                        → java.util.logging (can swap)
```

If you ever need to switch from Logback to Log4j2, you change only the dependency in `pom.xml` - zero code changes.

### 2. Log Formats

We use different formats for different environments because different environments have different consumers.

#### DEV Format - Human-Readable (Plain Text)

Developers read dev logs with their eyes. Colors and alignment make this easy:

```properties
# application-dev.yml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n"
```

**Example output:**
```
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] o.t.t.service.TaskService - Looking up task | taskId=550e8400
2026-02-27 10:30:00 INFO  [http-nio-8080-exec-1] o.t.t.service.TaskService - Task created | taskId=550e8400 | title=Fix bug
2026-02-27 10:30:01 WARN  [http-nio-8080-exec-2] o.t.t.config.LoggingAspect - Slow operation | duration=3200ms
2026-02-27 10:30:02 ERROR [http-nio-8080-exec-3] o.t.t.exception.GlobalEx... - Unexpected error | Path: /api/tasks
```

Each part explained:
- `2026-02-27 10:30:00` - When (timestamp)
- `DEBUG` - How severe (level, color-coded in terminal)
- `[http-nio-8080-exec-1]` - Which thread processed this request
- `o.t.t.service.TaskService` - Where (abbreviated class name)
- `Looking up task | taskId=550e8400` - What happened (message)

#### PROD Format - Machine-Readable (JSON)

In production, logs are consumed by **machines** (ELK Stack, Datadog, Splunk), not human eyes. JSON is perfect:

```json
{
    "timestamp": "2026-02-27T10:30:00.123Z",
    "level": "ERROR",
    "logger": "org.task.taskmaganer.service.TaskService",
    "thread": "http-nio-8080-exec-1",
    "message": "Failed to save task | taskId=550e8400",
    "correlationId": "a3f8b2c1",
    "stackTrace": "org.springframework.dao.DataAccessException..."
}
```

**Why JSON in production?**

| Feature | Plain Text | JSON |
|---------|-----------|------|
| Human readability | Easy to read | Hard to read |
| Machine parsing | Needs regex (fragile) | Native parsing (reliable) |
| Search/filter | `grep "ERROR"` (rough) | `jq '.level == "ERROR"'` (precise) |
| Structured queries | Impossible | "Show all errors for correlationId=a3f8b2c1" |
| Dashboard integration | Manual | Automatic with ELK/Datadog |

### 3. Correlation ID

**The Scenario That Explains Everything:**

> Customer calls support: "My task won't save."  
> Support asks: "What's your correlation ID?" (shown in the error popup)  
> Customer: "a3f8b2c1"  
> Support runs: `grep "a3f8b2c1" /var/log/task-manager.log`

Now they see the ENTIRE request flow:
```
10:30:00 INFO  [a3f8b2c1] → POST /api/v1/tasks
10:30:00 DEBUG [a3f8b2c1] Validating task request | title=My Task | priority=HIGH
10:30:00 DEBUG [a3f8b2c1] Checking user permissions | userId=john_doe
10:30:01 DEBUG [a3f8b2c1] Saving task to database...
10:30:01 ERROR [a3f8b2c1] Database connection refused | host=db.prod.internal:5432
10:30:01 ERROR [a3f8b2c1] ✕ POST /api/v1/tasks - 1200ms - Exception: Connection refused
```

**Without correlation ID**, these lines are mixed with hundreds of other concurrent requests. Finding the right ones is like finding a needle in a haystack.

**How it works in our code (`LoggingAspect.java`):**

```java
@Around("restController() && requestMapping()")
public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
    // 1. Get correlation ID from header (if client sent one) or generate a new one
    String correlationId = getOrCreateCorrelationId();
    
    // 2. Put it in MDC (Mapped Diagnostic Context) - automatically added to all log lines
    MDC.put("correlationId", correlationId);
    
    try {
        log.info("[{}] → {} {}", correlationId, method, path);  // Request log
        Object result = joinPoint.proceed();                     // Execute the actual method
        log.info("[{}] ← {} {} - {}ms", correlationId, method, path, duration); // Response log
        return result;
    } catch (Exception ex) {
        log.error("[{}] ✕ {} {} - {}ms - Exception: {}", 
                correlationId, method, path, duration, ex.getMessage(), ex);
        throw ex;
    } finally {
        MDC.remove("correlationId"); // Clean up
    }
}
```

**MDC (Mapped Diagnostic Context)** is like a thread-local sticky note. Once you write the correlation ID to MDC, every log line from that thread automatically includes it - even log lines in service classes that have no idea about HTTP requests.

---

## Environment-Based Logging

### DEV Environment

**Goal:** Maximum visibility for developers. See everything, including SQL queries.

```yaml
# application-dev.yml
logging:
  level:
    root: INFO
    org.task.taskmaganer: DEBUG         # Our code: see everything
    org.springframework.web: DEBUG      # Spring MVC: see request routing
    org.hibernate.SQL: DEBUG            # See all SQL queries
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE  # See query parameter values
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n"
```

**What DEV output looks like:**
```
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] o.t.t.config.LoggingAspect     - [a3f8b2c1] → POST /api/v1/tasks
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] o.t.t.service.TaskService      - Creating task | title=Fix login bug | priority=HIGH
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] org.hibernate.SQL              - insert into tasks (id, title, priority, status) values (?, ?, ?, ?)
2026-02-27 10:30:00 TRACE [http-nio-8080-exec-1] o.h.t.d.s.BasicBinder          - binding parameter [1] as [UUID] - [550e8400-...]
2026-02-27 10:30:00 TRACE [http-nio-8080-exec-1] o.h.t.d.s.BasicBinder          - binding parameter [2] as [VARCHAR] - [Fix login bug]
2026-02-27 10:30:00 INFO  [http-nio-8080-exec-1] o.t.t.service.TaskService      - Task created successfully | taskId=550e8400-...
2026-02-27 10:30:00 DEBUG [http-nio-8080-exec-1] o.t.t.config.LoggingAspect     - [a3f8b2c1] ← POST /api/v1/tasks - 45ms - OK
```

**Why?** Developers need to see SQL queries, parameter bindings, and full request flow to debug issues during development.

### TEST Environment

**Goal:** Moderate detail, saved to files for CI/CD pipeline review.

```yaml
# application-test.yml (recommended)
logging:
  level:
    root: INFO
    org.task.taskmaganer: INFO
    org.hibernate.SQL: DEBUG           # Still see SQL for debugging test failures
  file:
    name: logs/test-task-manager.log   # Save to file for CI artifacts
```

**Why file output?** When a CI/CD pipeline (Jenkins, GitHub Actions) runs tests and something fails, you need to download the log file as an artifact and read it. Console output is often truncated in CI systems.

### PROD Environment

**Goal:** Minimal noise, maximum value, secure, machine-parseable.

```yaml
# application-prod.yml
logging:
  level:
    root: WARN                           # Only warnings and errors from libraries
    org.task.taskmaganer: INFO           # Our code: important events + errors
    org.springframework.security: WARN   # Security events
    org.hibernate: ERROR                 # Only database errors (no SQL queries!)
  file:
    name: logs/task-manager.log          # Main log file
  logback:
    rollingpolicy:
      max-file-size: 100MB              # Rotate when file reaches 100MB
      max-history: 90                    # Keep 90 days of history
      total-size-cap: 50GB              # Never use more than 50GB total
```

**Production directory structure:**
```
/var/log/task-manager/
    task-manager.log              # Current log file (INFO+)
    task-manager.2026-02-26.0.log # Yesterday's log (rotated)
    task-manager.2026-02-25.0.log # Day before
    task-manager.2026-02-25.1.log # Day before (second file, if first hit 100MB)
    ...
```

**Why NO console output in production?**
- Production runs in Docker/Kubernetes - console output goes to container stdout
- Container stdout is already captured by the orchestrator
- Writing to both console and file doubles I/O for no benefit
- Log files are what you grep and ship to ELK/Datadog

**Why `org.hibernate: ERROR` in production?**
- SQL query logging at DEBUG level would generate **gigabytes** of logs per day
- Each request might execute 5-10 SQL queries
- 1000 requests/minute x 10 queries x 200 bytes = ~120 MB/hour of just SQL logs
- Only log Hibernate when something actually breaks

---

## What to Log vs What NEVER to Log

### Log These (Safe and Useful)

```java
// Entity IDs - safe, needed for debugging
log.info("Task updated | taskId={} | status={}", taskId, newStatus);

// Usernames - needed for audit trail
log.info("User logged in | username={} | ip={}", username, ipAddress);

// Operation durations - needed for performance monitoring
log.info("Task list loaded | count={} | duration={}ms", tasks.size(), duration);

// Business events - needed for audit
log.info("Task assigned | taskId={} | assignee={} | assignedBy={}", taskId, assignee, currentUser);

// Error details (server-side) - needed for debugging
log.error("Database connection failed | host={} | port={} | error={}", host, port, ex.getMessage(), ex);

// Request metadata - needed for security
log.info("API request | method={} | path={} | ip={} | userAgent={}", method, path, ip, userAgent);
```

### NEVER Log These (Dangerous)

```java
// NEVER: Passwords (even hashed ones in debug logs)
log.debug("User login attempt | username={} | password={}", username, password);  // VIOLATION!

// NEVER: JWT tokens (anyone who reads the log can impersonate the user)
log.info("Auth token generated | token={}", jwtToken);  // VIOLATION!

// NEVER: Credit card numbers (PCI-DSS violation: up to $500,000 fine)
log.info("Payment processed | card={}", cardNumber);  // VIOLATION!

// NEVER: National ID numbers / Social Security Numbers (GDPR/KVKK violation)
log.info("User verified | nationalId={}", nationalId);  // VIOLATION!

// NEVER: Full email addresses in high-volume logs (privacy)
log.debug("Sending email to {}", fullEmailAddress);  // VIOLATION!

// NEVER: API keys or secrets
log.debug("Calling external service | apiKey={}", apiKey);  // VIOLATION!
```

**The consequences are real:**
- **PCI-DSS violation** (logging credit card numbers): Fines up to **$500,000** + loss of ability to process payments
- **GDPR violation** (logging personal data without consent): Fines up to **4% of annual global revenue** or 20 million EUR
- **Security breach** (logging passwords/tokens): If logs are compromised, attackers get user credentials

### Careful - Mask These

```java
// Credit card: show only last 4 digits
log.info("Payment processed | card=****-****-****-{}", lastFourDigits);

// Email: show only the domain
log.info("Email sent | recipient=***@{}", emailDomain);

// Phone: show only last 3 digits
log.info("SMS sent | phone=****-***-{}", lastThreeDigits);

// IP: in some jurisdictions, IP is personal data - check your legal requirements
log.info("Request from ip={}", ipAddress);  // Usually OK, but check GDPR compliance
```

---

## Log Format Best Practices

### 1. Structured Logging

**Bad - Unstructured (hard to parse):**
```java
log.info("Task " + taskId + " was updated by user " + username + " at priority " + priority);
// Output: "Task 550e8400 was updated by user john at priority HIGH"
// Trying to extract taskId with regex? Good luck when the title contains "by user".
```

**Good - Key-Value Pairs (easy to parse):**
```java
log.info("Task updated | taskId={} | updatedBy={} | priority={}", taskId, username, priority);
// Output: "Task updated | taskId=550e8400 | updatedBy=john | priority=HIGH"
// Any log parser can split on " | " and then on "="
```

**Best - With MDC Context (automatic context):**
```java
MDC.put("taskId", taskId.toString());
MDC.put("userId", username);
log.info("Task updated | priority={}", priority);
// Output includes taskId and userId automatically in every log line from this thread
MDC.clear();
```

### 2. Context Information

**Insufficient (what went wrong WHERE?):**
```java
log.error("Database error");
// WHO was affected? WHAT operation? WHICH database? WHAT was the error?
```

**Ideal (everything you need to debug):**
```java
log.error("Failed to save task | taskId={} | operation=CREATE | table=tasks | error={} | duration={}ms",
        taskId, ex.getMessage(), duration, ex);
// WHO: taskId tells you which task
// WHAT: operation=CREATE
// WHERE: table=tasks
// WHY: error message and full stack trace (from 'ex')
// WHEN: timestamp is automatic
// HOW LONG: duration tells you if it was a timeout
```

### 3. Performance Metrics (Duration Tracking)

```java
// Pattern: measure, log, and flag slow operations
long start = System.currentTimeMillis();

List<Task> tasks = taskRepository.findAll();

long duration = System.currentTimeMillis() - start;

if (duration > 1000) {
    // This automatically becomes a WARN-level alert
    log.warn("Slow operation detected | operation=findAllTasks | duration={}ms | resultCount={}",
            duration, tasks.size());
} else {
    log.debug("Performance metric | operation=findAllTasks | duration={}ms | resultCount={}",
            duration, tasks.size());
}
```

This pattern is used in `AuditLogService.logPerformanceMetric()`:

```java
public void logPerformanceMetric(String operation, long durationMs, String details) {
    MDC.put("metricType", "PERFORMANCE");
    MDC.put("operation", operation);
    
    if (durationMs > 1000) {
        log.warn("Slow operation detected | operation={} | duration={}ms | details={}", 
                operation, durationMs, details);
    } else {
        log.debug("Performance metric | operation={} | duration={}ms", 
                operation, durationMs);
    }
    
    clearMdc();
}
```

---

## AOP Automatic Logging

### The Problem

You have 15 controller classes with 50+ endpoint methods. If you add `log.info("Request received...")` to each one manually:

```java
// This is what it looks like WITHOUT AOP - manual logging in every method
@GetMapping("/{id}")
public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
    log.info("→ GET /api/v1/tasks/{}", id);           // manual
    long start = System.currentTimeMillis();            // manual
    
    TaskResponse task = taskService.getTaskById(id);
    
    long duration = System.currentTimeMillis() - start; // manual
    log.info("← GET /api/v1/tasks/{} - {}ms", id, duration); // manual
    return ResponseEntity.ok(task);
}

// Repeat this in ALL 50+ methods. Miss one? Inconsistent logging.
// Need to change the format? Update 50+ methods.
```

### The Solution: AOP (Aspect-Oriented Programming)

**Instead of adding log.info() to every single controller method (50+ methods), AOP does it automatically. Write once, log everywhere.**

```java
@Aspect
@Component
public class LoggingAspect {

    // This pointcut targets ALL methods in ALL classes annotated with @RestController
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restController() {}

    // This runs AROUND every matched method (before + after)
    @Around("restController() && requestMapping()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String correlationId = getOrCreateCorrelationId();
        MDC.put("correlationId", correlationId);
        
        HttpServletRequest request = getCurrentRequest();
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String path = request != null ? request.getRequestURI() : "UNKNOWN";
        
        try {
            log.info("[{}] → {} {}", correlationId, method, path);
            
            Object result = joinPoint.proceed();  // Execute the actual controller method
            
            long duration = System.currentTimeMillis() - start;
            log.info("[{}] ← {} {} - {}ms", correlationId, method, path, duration);
            
            return result;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] ✕ {} {} - {}ms - Exception: {}", 
                    correlationId, method, path, duration, ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

**What your controller looks like WITH AOP (clean, no logging boilerplate):**

```java
@GetMapping("/{id}")
public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
    TaskResponse task = taskService.getTaskById(id);  // Just business logic!
    return ResponseEntity.ok(task);
}
// AOP automatically logs: "→ GET /api/v1/tasks/550e8400" and "← GET /api/v1/tasks/550e8400 - 23ms"
```

**Example output from AOP (you wrote zero logging code in the controller):**
```
10:30:00 INFO [a3f8b2c1] → GET /api/v1/tasks/550e8400-e29b-41d4-a716-446655440000
10:30:00 INFO [a3f8b2c1] ← GET /api/v1/tasks/550e8400-e29b-41d4-a716-446655440000 - 23ms
```

```
10:30:05 INFO [b4c9d3e2] → POST /api/v1/tasks
10:30:05 ERROR [b4c9d3e2] ✕ POST /api/v1/tasks - 150ms - Exception: Validation failed
```

**How AOP matches methods:**

The `@Pointcut` annotations define which methods get logged:
- `within(@RestController *)` - any class annotated with `@RestController`
- `@annotation(@GetMapping) || @annotation(@PostMapping) || ...` - methods with mapping annotations

Together: "Log every endpoint method in every controller, automatically."

---

## Audit Logging

### What's the Difference from Normal Logging?

| Aspect | Normal Logging | Audit Logging |
|--------|---------------|---------------|
| **Purpose** | Debugging and monitoring | Legal compliance and accountability |
| **What** | System events (errors, performance) | Business events (who did what) |
| **Audience** | Developers and DevOps | Managers, auditors, legal team |
| **Retention** | 30-90 days | Often 7+ years (legal requirement) |
| **Modification** | Can be rotated/deleted | Must be immutable (tamper-proof) |
| **Example** | "Database query took 200ms" | "User john_doe deleted task #42 at 14:30" |

**Analogy:** Normal logs are like your personal notes. Audit logs are like official court records - they must be accurate, complete, and preserved.

### AuditLogService Usage

```java
@Service
public class AuditLogService {
    
    // Uses a SEPARATE logger named "AUDIT" - can be routed to a separate file
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    // Task lifecycle events
    public void logTaskCreated(String taskId, String taskTitle, String createdBy, String assigneeId) {
        MDC.put("action", "CREATE");
        MDC.put("entityType", "TASK");
        MDC.put("entityId", taskId);
        MDC.put("userId", createdBy);
        
        auditLog.info("Task created | id={} | title={} | assignee={} | by={}", 
                taskId, taskTitle, assigneeId, createdBy);
        
        clearMdc();
    }
    
    public void logTaskUpdated(String taskId, String taskTitle, String updatedBy, 
                                Map<String, Object> changes) {
        MDC.put("action", "UPDATE");
        MDC.put("entityType", "TASK");
        MDC.put("entityId", taskId);
        MDC.put("userId", updatedBy);
        
        auditLog.info("Task updated | id={} | title={} | changes={} | by={}", 
                taskId, taskTitle, changes, updatedBy);
        
        clearMdc();
    }
    
    public void logTaskDeleted(String taskId, String taskTitle, String deletedBy) {
        MDC.put("action", "DELETE");
        MDC.put("entityType", "TASK");
        auditLog.info("Task deleted | id={} | title={} | by={}", taskId, taskTitle, deletedBy);
        clearMdc();
    }
    
    // Authentication events
    public void logUserLogin(String userId, String username, String ipAddress, boolean success) {
        MDC.put("action", "LOGIN");
        if (success) {
            auditLog.info("User login successful | username={} | ip={} | userId={}", 
                    username, ipAddress, userId);
        } else {
            auditLog.warn("User login failed | username={} | ip={}", username, ipAddress);
        }
        clearMdc();
    }
    
    // Security events - suspicious activities
    public void logSecurityEvent(String event, String userId, String ipAddress, String details) {
        MDC.put("action", "SECURITY");
        auditLog.warn("Security event | event={} | userId={} | ip={} | details={}", 
                event, userId, ipAddress, details);
        clearMdc();
    }
}
```

**How to use it in a service:**

```java
@Service
public class TaskService {
    
    private final AuditLogService auditLogService;
    
    public TaskResponse createTask(TaskRequest request, String currentUser) {
        // ... business logic to create task ...
        Task savedTask = taskRepository.save(task);
        
        // Log the business event for audit trail
        auditLogService.logTaskCreated(
                savedTask.getId().toString(),
                savedTask.getTitle(),
                currentUser,
                savedTask.getAssigneeId() != null ? savedTask.getAssigneeId().toString() : "unassigned"
        );
        
        return TaskMapper.toResponse(savedTask);
    }
}
```

---

## Logging Standards Checklist

### DEV Environment Checklist

- [x] Console output enabled with colored formatting
- [x] Log level set to DEBUG for application code
- [x] SQL queries visible (`org.hibernate.SQL: DEBUG`)
- [x] SQL parameters visible (`BasicBinder: TRACE`)
- [x] Spring Web request routing visible
- [x] AOP logging active (request/response logging)
- [ ] File output optional (not required for local dev)

### TEST Environment Checklist

- [x] Console + file output (for CI/CD artifacts)
- [x] Log level set to INFO for application code
- [x] SQL queries visible (for debugging test failures)
- [x] Test-specific log file name (e.g., `logs/test-task-manager.log`)
- [ ] Log file included in CI/CD artifacts configuration

### PROD Environment Checklist

- [x] File output only (no console output)
- [x] Log level set to INFO for application, WARN for root
- [x] JSON format configured for log aggregation tools
- [x] Log file rotation enabled (max 100MB per file)
- [x] Log history retention configured (90 days)
- [x] Total disk cap configured (50GB)
- [x] Separate audit log file
- [x] No passwords, tokens, or PII in logs
- [x] Hibernate logging set to ERROR only
- [x] Correlation ID included in every request

---

## Real Scenarios

### Scenario 1: Production Error - "Customer can't create a task"

**The situation:** A customer calls support at 3 PM saying "I can't create a new task, it just shows an error."

**Step 1:** Get the correlation ID from the customer (displayed in the error message or browser console).

```bash
# Search for the correlation ID in today's logs
grep "c7d9e1f3" /var/log/task-manager/task-manager.log
```

**Step 2:** Read the request flow:

```
15:00:01 INFO  [c7d9e1f3] → POST /api/v1/tasks
15:00:01 DEBUG [c7d9e1f3] Validating task request | title=Q4 Report | priority=HIGH
15:00:01 DEBUG [c7d9e1f3] User authenticated | userId=sarah_m | role=MEMBER
15:00:02 ERROR [c7d9e1f3] Failed to save task | error=Column 'project_id' cannot be null
15:00:02 ERROR [c7d9e1f3] ✕ POST /api/v1/tasks - 1200ms - Exception: DataIntegrityViolationException
    org.springframework.dao.DataIntegrityViolationException: Column 'project_id' cannot be null
        at org.task.taskmaganer.service.TaskService.createTask(TaskService.java:87)
```

**Step 3:** Root cause found in 2 minutes: `project_id` is null because the frontend form isn't sending the project ID field. The recent UI update accidentally removed the project selector.

### Scenario 2: Performance Problem - "Task listing is slow"

**The situation:** Users report that the "My Tasks" page takes 10+ seconds to load.

**Step 1:** Search for slow operation warnings:

```bash
grep "Slow operation" /var/log/task-manager/task-manager.log | tail -20
```

**Step 2:** Find the pattern:

```
14:00:01 WARN  [a1b2c3d4] Slow operation detected | operation=findTasksByUser | duration=8500ms | resultCount=2847
14:00:05 WARN  [e5f6g7h8] Slow operation detected | operation=findTasksByUser | duration=9200ms | resultCount=3102
14:00:10 WARN  [i9j0k1l2] Slow operation detected | operation=findTasksByUser | duration=12000ms | resultCount=2500
```

**Step 3:** Root cause: The `findTasksByUser` query is loading thousands of tasks without pagination. The `resultCount=2847` tells you the problem - no one needs to see 2847 tasks at once.

**Fix:** Add pagination (`LIMIT 20 OFFSET 0`) and the query drops from 8500ms to 15ms.

### Scenario 3: Security Audit - "Suspicious user activity"

**The situation:** An admin notices unusual data deletions.

**Step 1:** Check the audit log for delete operations:

```bash
grep "Task deleted" /var/log/task-manager/audit.log | grep "2026-02-27"
```

**Step 2:** Find the suspicious pattern:

```
09:00:01 INFO  AUDIT Task deleted | id=task-001 | title=Project Alpha | by=mike_t
09:00:03 INFO  AUDIT Task deleted | id=task-002 | title=Project Beta  | by=mike_t
09:00:05 INFO  AUDIT Task deleted | id=task-003 | title=Q4 Budget     | by=mike_t
09:00:07 INFO  AUDIT Task deleted | id=task-004 | title=Client Report | by=mike_t
... 50 more deletions in 2 minutes
```

**Step 3:** Check the security log for this user:

```bash
grep "mike_t" /var/log/task-manager/audit.log | grep "LOGIN"
```

```
08:55:00 INFO  AUDIT User login successful | username=mike_t | ip=185.220.101.42 | userId=mike_t
```

**Step 4:** That IP `185.220.101.42` is a Tor exit node (suspicious). Mike's usual IP is `192.168.1.50`. Conclusion: Mike's account was likely compromised. Disable the account, restore deleted tasks from backups, and require password reset.

**Without audit logs, you would never know WHO deleted the tasks, WHEN, or FROM WHERE.**

---

## Decision Tree: "Which Log Level Should I Use?"

Use this mental flowchart when deciding which log level to use:

```
START: Something happened in your code
  │
  ├─ Is this a failure/crash/exception?
  │   ├─ YES → Is it OUR fault (server error)?
  │   │   ├─ YES → ERROR  (e.g., NullPointerException, DB down)
  │   │   └─ NO  → WARN   (e.g., invalid user input, 404)
  │   └─ NO → continue below
  │
  ├─ Is this a notable business event?
  │   ├─ YES → INFO  (e.g., "Task created", "User logged in", "App started")
  │   └─ NO → continue below
  │
  ├─ Is this useful ONLY for debugging?
  │   ├─ YES → DEBUG  (e.g., variable values, SQL queries, method entry/exit)
  │   └─ NO → continue below
  │
  └─ You probably don't need to log this.
```

**Quick reference table:**

| Situation | Level | Why |
|-----------|-------|-----|
| Application started | INFO | Notable event |
| User logged in | INFO | Business event, audit trail |
| Task created/updated/deleted | INFO | Business event |
| Database query took 3 seconds | WARN | Degraded performance, not a crash |
| User sent invalid JSON | WARN | Client's fault, not ours |
| Failed login attempt | WARN | Security monitoring |
| NullPointerException | ERROR | Bug in our code |
| Database connection refused | ERROR | Infrastructure failure |
| External service timeout | ERROR | Integration failure |
| Variable value during debugging | DEBUG | Only useful during investigation |
| SQL query content | DEBUG | Only useful during development |
| Method entry/exit | DEBUG | Only useful during investigation |

---

## Summary - The 7 Golden Rules of Logging

### Rule 1: Use the Right Level
DEBUG for investigation, INFO for business events, WARN for degradations, ERROR for failures. Never use ERROR for client mistakes (that's WARN).

### Rule 2: Never Log Secrets
No passwords, tokens, credit cards, or national IDs. Ever. The fine alone could bankrupt a startup. Use masking for sensitive fields.

### Rule 3: Always Include Context
Bad: `"Error occurred"`. Good: `"Failed to save task | taskId=550e8400 | error=Connection refused | duration=1200ms"`. Every log line should answer: WHO, WHAT, WHERE, WHEN, WHY.

### Rule 4: Use Structured Format
Use key-value pairs (`taskId=550e8400`) instead of prose (`"task 550e8400 was created"`). Machines need to parse your logs too.

### Rule 5: Different Environments, Different Configs
DEV = verbose + console + SQL visible. PROD = concise + file only + JSON format + no SQL. One size does not fit all.

### Rule 6: Use Correlation IDs
Tag every request with a unique ID. When debugging, this ID lets you trace the entire request flow across all log lines, services, and microservices.

### Rule 7: Automate with AOP
Don't manually add request/response logging to every controller method. AOP gives you consistent, automatic logging with zero boilerplate in your business code.
