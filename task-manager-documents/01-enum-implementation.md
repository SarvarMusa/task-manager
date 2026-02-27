# Enum Usage - TaskPriority and TaskStatus

## Date: 2026-02-26

## Problem

Before this change, the `priority` and `status` fields on our Task entity were plain `String` types. This sounds harmless at first — after all, strings can hold any text, right? That's exactly the problem.

### Why Strings Are Dangerous for Fixed-Value Fields

Imagine a traffic light that accepts **any color you give it**. Someone could set it to "purple," "rainbow," or "GO_FASTER" — and the whole intersection breaks down because no driver knows what those mean. That is exactly what happens when you use a `String` for a field that should only have a small, fixed set of values.

Here is what our old code looked like:

```java
// OLD CODE - The dangerous way (String fields)
@Column(name = "priority", nullable = false)
private String priority;  // Any text goes here. Anything at all.

@Column(name = "status", nullable = false)
private String status;    // Same problem here.
```

Now look at the chaos this creates. All of these API requests would be **accepted without any error**:

```json
// Request 1: Correct - works fine
{
  "title": "Fix login bug",
  "priority": "HIGH",
  "status": "PENDING"
}

// Request 2: Typo - also accepted! No error!
{
  "title": "Fix login bug",
  "priority": "HGIH",
  "status": "PENDNG"
}

// Request 3: Made-up values - also accepted!
{
  "title": "Fix login bug",
  "priority": "SUPER_ULTRA_MEGA_HIGH",
  "status": "KINDA_DONE_I_THINK"
}

// Request 4: Different casing - is this the same as "HIGH"?
{
  "title": "Fix login bug",
  "priority": "high",
  "status": "Pending"
}
```

Every single one of these gets saved to your database. Your database now has rows where `priority` is "HIGH", "high", "High", "HGIH", and "SUPER_ULTRA_MEGA_HIGH". Good luck filtering by priority now.

### The Real-World Consequences

Here is what actually breaks when you allow free-text strings for fixed values:

**1. Filtering stops working:**
```java
// You want all high-priority tasks. But your database has:
// "HIGH", "high", "High", "HGIH", "hi", "SUPER_HIGH"
// This query only returns SOME of them:
List<Task> highTasks = taskRepository.findByPriority("HIGH");
// Result: Misses "high", "High", and all the misspellings
```

**2. Business logic becomes a defensive nightmare:**
```java
// Without enums, you have to check for every possible value manually
public void processTask(Task task) {
    String status = task.getStatus();
    
    // Is it "COMPLETED"? "completed"? "Complete"? "DONE"? "done"? "Finished"?
    // You have no idea what's in the database, so you check everything:
    if (status.equalsIgnoreCase("COMPLETED") || 
        status.equalsIgnoreCase("DONE") || 
        status.equalsIgnoreCase("FINISHED")) {
        // handle completed task
    }
    // This is madness. And it WILL miss edge cases.
}
```

**3. No compile-time safety:**
```java
// This compiles perfectly. The bug won't be caught until production.
task.setStatus("COMPLTED");  // Typo! But the compiler says "looks good to me!"
```

**4. Documentation is unreliable:**
Your Swagger/OpenAPI docs can say "priority should be HIGH, MEDIUM, or LOW" — but nothing **enforces** it. A developer reading the docs might still accidentally type "Mid" or "medium" or "M".

Think of it this way: using a `String` for status is like putting a text field on a form where you should have a dropdown. People **will** type the wrong thing.

---

## Solution

We replaced the `String` fields with **Java Enums**. An enum is a special Java type that defines a fixed, closed set of allowed values. The compiler and the framework enforce these values — not just documentation, not just hope.

### Step 1: Create the Enum Classes

```java
// File: entity/TaskPriority.java
package org.task.taskmaganer.entity;

public enum TaskPriority {
    LOW,       // Not urgent, handle when convenient
    MEDIUM,    // Normal priority, standard workflow
    HIGH       // Urgent, needs immediate attention
}
// That's it. These are the ONLY three legal values. Period.
```

```java
// File: entity/TaskStatus.java
package org.task.taskmaganer.entity;

public enum TaskStatus {
    PENDING,       // Task created but not started
    IN_PROGRESS,   // Someone is actively working on it
    COMPLETED,     // Task is done
    CANCELLED      // Task was abandoned or is no longer needed
}
// Four values. No more, no less. The compiler enforces this.
```

### Step 2: Update the Entity

```java
// File: entity/Task.java — BEFORE (String)
@Column(name = "priority", nullable = false)
private String priority;

// File: entity/Task.java — AFTER (Enum)
@Column(name = "priority", nullable = false)
@Enumerated(EnumType.STRING)   // Store as "HIGH", "MEDIUM", "LOW" in the DB
private TaskPriority priority;  // Now it's an enum, not a string

@Column(name = "status", nullable = false)
@Enumerated(EnumType.STRING)   // Store as "PENDING", "IN_PROGRESS", etc.
private TaskStatus status;      // Same here
```

The `@Enumerated(EnumType.STRING)` annotation tells JPA to store the enum's **name** as a string in the database column. So the database still has a readable "HIGH" or "PENDING" value — but Java enforces that only valid values can be set.

> **Why `EnumType.STRING` and not `EnumType.ORDINAL`?**
> `ORDINAL` stores the enum's position (0, 1, 2...). If you later reorder the enum values or add one in the middle, all your existing data becomes wrong. `STRING` is always safe because it stores the actual name.

### Step 3: Update the DTOs

```java
// File: dto/request/CreateTaskRequest.java — BEFORE
private String priority;
private String status;

// File: dto/request/CreateTaskRequest.java — AFTER
@NotNull(message = "Priority is required")
private TaskPriority priority;  // Jackson will auto-deserialize "HIGH" → TaskPriority.HIGH

@NotNull(message = "Status is required")
private TaskStatus status;      // Jackson will auto-deserialize "PENDING" → TaskStatus.PENDING
```

Same change for `UpdateTaskRequest`:

```java
// File: dto/request/UpdateTaskRequest.java
private TaskPriority priority;  // Optional field for partial updates
private TaskStatus status;      // Optional field for partial updates
```

### Step 4: Update the Repository

The repository methods now accept enum types instead of strings:

```java
// File: repository/TaskRepository.java
// Parameters are now type-safe enum values, not arbitrary strings
Page<Task> findByStatus(TaskStatus status, Pageable pageable);
Page<Task> findByPriority(TaskPriority priority, Pageable pageable);

// JPQL queries also use the enum type directly
@Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.status = :status")
Page<Task> findByUserIdAndStatus(@Param("userId") UUID userId, 
                                  @Param("status") TaskStatus status, 
                                  Pageable pageable);
```

### Step 5: Update the Service Layer

```java
// File: service/TaskService.java
// No string parsing needed — the DTO already has the correct enum type
public TaskResponse createTask(CreateTaskRequest request) {
    Task task = new Task();
    task.setPriority(request.getPriority());  // TaskPriority enum, guaranteed valid
    task.setStatus(request.getStatus());      // TaskStatus enum, guaranteed valid
    // ...
}
```

### Step 6: Update the Controller

```java
// File: controller/TaskController.java
// Path variables are now enums — Spring auto-converts them
@GetMapping("/status/{status}")
public ResponseEntity<PageResponse<TaskResponse>> getTasksByStatus(
        @PathVariable TaskStatus status,  // Spring converts "PENDING" → TaskStatus.PENDING
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = createPageable(page, size, sort);
    PageResponse<TaskResponse> response = taskService.getTasksByStatus(status, pageable);
    return ResponseEntity.ok(response);
}
```

---

## What We Gained

### 1. Type Safety (Catch Bugs at Compile Time)

```java
// WITH STRINGS — this compiles fine, bug hides until production
task.setStatus("COMPLTED");  // Typo — no error!

// WITH ENUMS — this won't even compile
task.setStatus(TaskStatus.COMPLTED);  // Compiler error: COMPLTED is not a valid TaskStatus
// The IDE underlines it in red immediately. Bug caught in 0 seconds.
```

This is the single biggest win. Bugs that would have reached production are now caught the instant you write the code.

### 2. Automatic Validation (Spring Returns 400 for Free)

When someone sends an invalid value in a JSON request, Spring Boot + Jackson automatically rejects it with a `400 Bad Request`. You don't have to write a single line of validation code.

```bash
# Sending an invalid priority
curl -X POST http://localhost:8080/tasks/ \
  -H "Content-Type: application/json" \
  -d '{"title": "Test", "priority": "SUPER_HIGH", "status": "PENDING", "userId": "..."}'
```

Spring automatically responds with:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "JSON parse error: Cannot deserialize value of type `TaskPriority` from String \"SUPER_HIGH\": not one of the values accepted for Enum class: [LOW, MEDIUM, HIGH]"
}
```

You wrote **zero** validation code for this. Spring does it automatically because the field is an enum.

### 3. Case-Insensitive Support (via Spring Configuration)

By default, Jackson (the JSON library Spring uses) is case-sensitive for enums. But you can make it case-insensitive with one line in `application.properties`:

```properties
# application.properties
spring.jackson.mapper.accept-case-insensitive-enums=true
```

Now all of these work:
```json
{"priority": "HIGH"}      // works
{"priority": "high"}      // also works
{"priority": "High"}      // also works
{"priority": "hIgH"}      // also works (yes, really)
```

This is incredibly useful for real-world APIs where different clients might use different casing conventions.

### 4. IDE Auto-Complete

When you type `TaskPriority.` in your IDE, you immediately see all valid options:

```
TaskPriority.LOW
TaskPriority.MEDIUM
TaskPriority.HIGH
```

No need to check documentation, no need to remember the exact string. Your IDE tells you. This speeds up development and eliminates typo-based bugs.

### 5. Swagger/OpenAPI Documentation

Because the fields are enums, Swagger automatically generates a dropdown in its UI showing the valid values. API consumers can see exactly what's allowed without reading a single line of documentation:

```yaml
# Auto-generated OpenAPI spec
priority:
  type: string
  enum:
    - LOW
    - MEDIUM
    - HIGH
status:
  type: string
  enum:
    - PENDING
    - IN_PROGRESS
    - COMPLETED
    - CANCELLED
```

### 6. Business Logic Simplicity

```java
// WITH STRINGS — fragile, verbose, error-prone
if (task.getStatus().equals("COMPLETED") || task.getStatus().equals("completed")) {
    // ...
}

// WITH ENUMS — clean, safe, impossible to get wrong
if (task.getStatus() == TaskStatus.COMPLETED) {
    // This is the ONLY way to check. No ambiguity. No edge cases.
}
```

You can also use enums in `switch` statements:

```java
switch (task.getPriority()) {
    case HIGH -> notifyManager();        // Urgent — alert the manager
    case MEDIUM -> addToSprintBacklog(); // Normal — plan it for the sprint
    case LOW -> addToIceBox();           // Low priority — we'll get to it
    // No default needed — the compiler warns you if you miss a case!
}
```

That last point is powerful: if you later add a new priority like `CRITICAL`, the compiler will warn you about every `switch` statement that doesn't handle it.

---

## Advantages

| Advantage | Explanation | Example |
|---|---|---|
| **Compile-time safety** | Invalid values cause compiler errors, not runtime crashes | `TaskStatus.COMPLTED` → red underline in IDE |
| **Automatic API validation** | Spring returns 400 for invalid enum values automatically | `"SUPER_HIGH"` → 400 Bad Request |
| **Case-insensitive input** | One config line accepts any casing | `"high"`, `"HIGH"`, `"High"` all work |
| **IDE auto-complete** | Type `TaskPriority.` and see all options | Eliminates typos during development |
| **Self-documenting API** | Swagger shows enum values as a dropdown | No need for external docs |
| **Clean business logic** | Use `==` comparison and `switch` statements | `task.getStatus() == TaskStatus.COMPLETED` |
| **Database consistency** | Only valid values can exist in the DB | No more "HGIH" or "pendng" rows |
| **Refactoring safety** | Rename an enum value → compiler shows every place to update | Change `CANCELLED` to `CANCELED` safely |

## Disadvantages

| Disadvantage | Explanation | Solution |
|---|---|---|
| **Adding new values requires deployment** | You can't add a new status at runtime — you need to change the code and redeploy | Acceptable trade-off for type safety. In practice, priority/status values rarely change. |
| **Database migration needed** | If you rename or remove an enum value, existing rows must be updated | Write a SQL migration script: `UPDATE tasks SET status = 'CANCELLED' WHERE status = 'CANCELED';` |
| **Case sensitivity by default** | Jackson rejects `"high"` if you don't configure it | Add `spring.jackson.mapper.accept-case-insensitive-enums=true` to `application.properties` |
| **Serialization format coupling** | `EnumType.STRING` means the DB column value matches the Java enum name exactly | Use consistent naming from the start. Avoid renaming enum values unless necessary. |
| **Breaking change for existing clients** | If clients were sending arbitrary strings that happened to work, they'll get 400 errors now | Communicate the change. Provide the list of valid values. Use case-insensitive mode. |

---

## Affected Files

- [x] `entity/TaskPriority.java` — **New file**: enum with `LOW`, `MEDIUM`, `HIGH`
- [x] `entity/TaskStatus.java` — **New file**: enum with `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
- [x] `entity/Task.java` — Changed `String priority` → `TaskPriority priority`, `String status` → `TaskStatus status`, added `@Enumerated(EnumType.STRING)`
- [x] `dto/request/CreateTaskRequest.java` — Changed field types from `String` to `TaskPriority`/`TaskStatus`
- [x] `dto/request/UpdateTaskRequest.java` — Changed field types from `String` to `TaskPriority`/`TaskStatus`
- [x] `dto/response/TaskResponse.java` — Converts enum to string via `.name()` for JSON output
- [x] `repository/TaskRepository.java` — Method parameters changed from `String` to `TaskPriority`/`TaskStatus`
- [x] `service/TaskService.java` — No string parsing needed; works directly with enum types
- [x] `controller/TaskController.java` — `@PathVariable` parameters now use enum types; Spring auto-converts

---

## API Usage Examples

### Creating a Task (Valid Request)

**Request:**
```bash
curl -X POST http://localhost:8080/tasks/ \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Set up CI/CD pipeline",
    "description": "Configure GitHub Actions for automated testing and deployment",
    "priority": "HIGH",
    "status": "PENDING",
    "userId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

**Response (201 Created):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "title": "Set up CI/CD pipeline",
  "description": "Configure GitHub Actions for automated testing and deployment",
  "priority": "HIGH",
  "status": "PENDING",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "isActive": true,
  "createdAt": "2026-02-26T10:30:00",
  "updatedAt": "2026-02-26T10:30:00"
}
```

### Sending an Invalid Priority

**Request:**
```bash
curl -X POST http://localhost:8080/tasks/ \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Some task",
    "priority": "SUPER_HIGH",
    "status": "PENDING",
    "userId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

**Response (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "JSON parse error: Cannot deserialize value of type `org.task.taskmaganer.entity.TaskPriority` from String \"SUPER_HIGH\": not one of the values accepted for Enum class: [LOW, MEDIUM, HIGH]"
}
```

### Sending an Invalid Status

**Request:**
```bash
curl -X POST http://localhost:8080/tasks/ \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Some task",
    "priority": "HIGH",
    "status": "DONE",
    "userId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

**Response (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "JSON parse error: Cannot deserialize value of type `org.task.taskmaganer.entity.TaskStatus` from String \"DONE\": not one of the values accepted for Enum class: [PENDING, IN_PROGRESS, COMPLETED, CANCELLED]"
}
```

### Filtering Tasks by Status (Path Variable)

**Request:**
```bash
# Spring auto-converts the path variable "COMPLETED" to TaskStatus.COMPLETED
curl http://localhost:8080/tasks/status/COMPLETED?page=0&size=10
```

**Invalid path variable:**
```bash
curl http://localhost:8080/tasks/status/FINISHED
# Returns 400 — "FINISHED" is not a valid TaskStatus
```

### Updating a Task with Enum Values

**Request:**
```bash
curl -X PUT http://localhost:8080/tasks/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_PROGRESS",
    "priority": "MEDIUM"
  }'
```

**Response (200 OK):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "title": "Set up CI/CD pipeline",
  "description": "Configure GitHub Actions for automated testing and deployment",
  "priority": "MEDIUM",
  "status": "IN_PROGRESS",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "isActive": true,
  "createdAt": "2026-02-26T10:30:00",
  "updatedAt": "2026-02-26T14:45:00"
}
```

---

## Conclusion

Replacing `String` fields with Java enums for `TaskPriority` and `TaskStatus` is one of the highest-value, lowest-effort improvements you can make to a Spring Boot application. The change is small — two new enum files and updating field types across a few classes — but the benefits are enormous:

- **Invalid data can no longer enter your system.** Spring rejects it at the API boundary before your code ever sees it.
- **Bugs are caught at compile time**, not in production at 3 AM.
- **Your API is self-documenting.** Swagger shows the valid values. No guesswork for consumers.
- **Business logic becomes cleaner.** No more defensive string comparisons scattered everywhere.

The general rule is straightforward: **if a field has a fixed, known set of values, it should be an enum.** Strings are for free-form text like names, descriptions, and comments. Enums are for categories, statuses, priorities, roles, and anything else where the valid options are known in advance.
