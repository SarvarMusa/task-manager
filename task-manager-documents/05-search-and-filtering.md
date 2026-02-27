# Search and Filtering Implementation

## Date: 2026-02-27

---

## Problem

### Why Single-Criteria Filtering Is Not Enough

Imagine Amazon only letting you filter by **ONE thing** at a time -- either by price OR by category OR by rating, but **never together**. You search for headphones, and you can pick "Electronics" or "$50-$100" or "4+ stars", but not all three at once. You'd scroll through thousands of irrelevant results forever. You'd never find what you want.

That is exactly what our Task Manager looked like before this change. We had endpoints like:

```
GET /tasks/status/{status}           -- filter by status only
GET /tasks/priority/{priority}       -- filter by priority only
GET /tasks/user/{userId}             -- filter by user only
GET /tasks/active                    -- filter by active only
GET /tasks/user/{userId}/status/{status}  -- filter by user + status
```

Each endpoint handles **one specific combination** of filters. But what if a user wants:

> "Show me all **HIGH** priority tasks that are **IN_PROGRESS**, assigned to **user X**, created **after January 2026**, with the word **'deploy'** in the title."

There is no endpoint for that. We'd have to create one. And then another for every other combination.

### The Method Explosion Problem

Here is the math that makes this approach unsustainable:

Our Task entity has **8 filterable fields**: `searchQuery`, `status`, `priority`, `userId`, `isActive`, `dueDateFrom`, `dueDateTo`, `createdAt` range.

Each filter is either **included** or **not included** in a query. That means:

```
2^8 = 256 possible filter combinations
```

That's **256 separate repository methods** and **256 controller endpoints**. Even if you only cover the "common" ones, you end up with 20+ methods that look almost identical:

```java
// We already had these in TaskRepository -- look how they multiply:
Page<Task> findByStatus(TaskStatus status, Pageable pageable);
Page<Task> findByPriority(TaskPriority priority, Pageable pageable);
Page<Task> findByUserId(UUID userId, Pageable pageable);
Page<Task> findActiveTasksByUserId(UUID userId, Pageable pageable);
Page<Task> findByUserIdAndStatus(UUID userId, TaskStatus status, Pageable pageable);
Page<Task> findByUserIdAndPriority(UUID userId, TaskPriority priority, Pageable pageable);
Page<Task> findAllActiveTasks(Pageable pageable);
Page<Task> findAllActiveTasksByStatus(TaskStatus status, Pageable pageable);
Page<Task> findAllActiveTasksByPriority(TaskPriority priority, Pageable pageable);
// ... and this is only ~9 out of 256 possible combinations!
```

And in the controller, we had **11 separate GET endpoints** just for different filter combinations. Each one essentially does the same thing: call a repository method and return paginated results.

**This doesn't scale.** Every new filter doubles the combinations. Adding a `dueDate` filter to the above? Now it's 512 combinations.

---

## Solution

### The JPA Specification Pattern

Instead of writing one method per combination, we write **one small Specification per filter**, then **combine them dynamically** at runtime. Think of it like building blocks:

```
Block 1: status = IN_PROGRESS     (use it or skip it)
Block 2: priority = HIGH           (use it or skip it)
Block 3: userId = some-uuid        (use it or skip it)
Block 4: search text in title      (use it or skip it)
...combine whichever blocks the user provides...
```

**Without Specification:** 8 optional filters = 2^8 = 256 possible combinations = 256 separate methods.
**With Specification:** 8 small specs + 1 composite method. That's the magic.

### 1. TaskSpecification Class

The heart of the solution. Each static method creates one small filter. The `withFilters()` method chains them all together. If a parameter is `null`, that filter is silently skipped (returns `cb.conjunction()` -- which means "always true", effectively a no-op).

**File:** `src/main/java/org/task/taskmaganer/specification/TaskSpecification.java`

```java
public class TaskSpecification {

    // Private constructor -- this is a utility class, never instantiate it
    private TaskSpecification() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    // ==================== THE MAIN METHOD ====================
    // Combines ALL filter criteria into one Specification.
    // Null criteria are automatically ignored (they become "always true").
    
    public static Specification<Task> withFilters(TaskFilterCriteria criteria) {
        return Specification.where(
                withSearchQuery(criteria.searchQuery())       // text search in title & description
                        .and(withStatus(criteria.status()))   // exact status match
                        .and(withPriority(criteria.priority())) // exact priority match
                        .and(withUserId(criteria.userId()))   // tasks belonging to a user
                        .and(withIsActive(criteria.isActive())) // active/inactive filter
                        .and(withDueDateBetween(criteria.dueDateFrom(), criteria.dueDateTo()))
                        .and(withCreatedAtBetween(criteria.createdAtFrom(), criteria.createdAtTo()))
        );
    }

    // ==================== INDIVIDUAL SPECIFICATIONS ====================

    // Full-text search: looks for the search term in BOTH title and description
    // Uses LIKE with lowercase for case-insensitive matching
    public static Specification<Task> withSearchQuery(String searchQuery) {
        return (root, query, cb) -> {
            if (isEmpty(searchQuery)) {
                return cb.conjunction(); // null/empty search = skip this filter
            }
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";
            Predicate titleMatch = cb.like(cb.lower(root.get(Task.Fields.title)), pattern);
            Predicate descMatch = cb.like(cb.lower(root.get(Task.Fields.description)), pattern);
            return cb.or(titleMatch, descMatch); // match in EITHER field
        };
    }

    // Status filter: if null, skip. If provided, exact match.
    public static Specification<Task> withStatus(TaskStatus status) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.status), status);
    }

    // Priority filter: same pattern as status
    public static Specification<Task> withPriority(TaskPriority priority) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.priority), priority);
    }

    // User filter: navigates the relationship Task -> User -> id
    public static Specification<Task> withUserId(UUID userId) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.user).get("id"), userId);
    }

    // Active/inactive filter
    public static Specification<Task> withIsActive(Boolean isActive) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.isActive), isActive);
    }

    // Date range: supports from-only, to-only, or both
    public static Specification<Task> withDueDateBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) ->
                dateRangePredicate(cb, root.get(Task.Fields.dueDate), from, to);
    }

    public static Specification<Task> withCreatedAtBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) ->
                dateRangePredicate(cb, root.get(Task.Fields.createdAt), from, to);
    }

    // ==================== HELPER: equals with null safety ====================
    // If value is null, return conjunction() (= always true = skip this filter)
    // If value is present, return an equals predicate
    private static <T> Predicate equalsPredicate(
            CriteriaBuilder cb, Path<T> path, T value) {
        return Optional.ofNullable(value)
                .map(v -> cb.equal(path, v))
                .orElse(cb.conjunction());
    }

    // ==================== HELPER: date range with null safety ====================
    // Supports: from only, to only, both, or neither
    private static <T extends Comparable<? super T>> Predicate dateRangePredicate(
            CriteriaBuilder cb, Path<T> path, T from, T to) {
        List<Predicate> predicates = new ArrayList<>();
        Optional.ofNullable(from)
                .ifPresent(f -> predicates.add(cb.greaterThanOrEqualTo(path, f)));
        Optional.ofNullable(to)
                .ifPresent(t -> predicates.add(cb.lessThanOrEqualTo(path, t)));
        if (predicates.isEmpty()) {
            return cb.conjunction(); // no dates provided = skip
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
```

**Key insight:** Every single spec method returns `cb.conjunction()` (SQL: `1=1`, which means "always true") when its parameter is null. This means null filters are **silently ignored** -- they don't break the query, they just don't add a WHERE clause. That's what makes the dynamic combination work.

### 2. TaskFilterCriteria Record

A simple immutable data holder that groups all filter parameters together. Uses Java's `record` type with a Builder pattern:

```java
public record TaskFilterCriteria(
        String searchQuery,
        TaskStatus status,
        TaskPriority priority,
        UUID userId,
        Boolean isActive,
        LocalDateTime dueDateFrom,
        LocalDateTime dueDateTo,
        LocalDateTime createdAtFrom,
        LocalDateTime createdAtTo
) {
    // Compact constructor trims whitespace from search query
    public TaskFilterCriteria {
        if (searchQuery != null) {
            searchQuery = searchQuery.trim();
        }
    }

    // Builder pattern for convenient construction
    public static Builder builder() { return new Builder(); }
    
    // Usage:
    // TaskFilterCriteria.builder()
    //     .status(TaskStatus.IN_PROGRESS)
    //     .priority(TaskPriority.HIGH)
    //     .build();
}
```

### 3. SearchTaskRequest DTO

The request body that the client sends. It maps 1-to-1 with the filter criteria, and has a `toFilterCriteria()` conversion method:

**File:** `src/main/java/org/task/taskmaganer/dto/request/SearchTaskRequest.java`

```java
public class SearchTaskRequest {
    private String searchQuery;      // text to search in title & description
    private TaskStatus status;       // PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    private TaskPriority priority;   // LOW, MEDIUM, HIGH
    private UUID userId;             // filter by assigned user
    private Boolean isActive;        // true = active tasks only
    private LocalDateTime dueDateFrom;    // due date range start
    private LocalDateTime dueDateTo;      // due date range end
    private LocalDateTime createdAtFrom;  // creation date range start
    private LocalDateTime createdAtTo;    // creation date range end

    // ALL fields are optional! Null = don't filter by this field.

    // Converts this DTO to the internal filter criteria object
    public TaskSpecification.TaskFilterCriteria toFilterCriteria() {
        return new TaskSpecification.TaskFilterCriteria(
                searchQuery, status, priority, userId, isActive,
                dueDateFrom, dueDateTo, createdAtFrom, createdAtTo
        );
    }
    
    // ... getters and setters ...
}
```

### 4. Repository with JpaSpecificationExecutor

The repository just needs to extend `JpaSpecificationExecutor<Task>`. That's it -- one line gives us full Specification support:

**File:** `src/main/java/org/task/taskmaganer/repository/TaskRepository.java`

```java
@Repository
public interface TaskRepository extends 
        JpaRepository<Task, UUID>,              // standard CRUD
        JpaSpecificationExecutor<Task> {        // <-- THIS IS THE KEY LINE

    // The old single-filter methods still work:
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    Page<Task> findByPriority(TaskPriority priority, Pageable pageable);
    // ...

    // JpaSpecificationExecutor gives us this method FOR FREE:
    // Page<Task> findAll(Specification<Task> spec, Pageable pageable);
    // We don't even need to declare it -- it's inherited!
}
```

### 5. Service Methods

Two new methods in TaskService -- one for the full multi-criteria search (POST), one for simple text search (GET):

**File:** `src/main/java/org/task/taskmaganer/service/TaskService.java`

```java
// Advanced multi-criteria search
public PageResponse<TaskResponse> searchTasks(SearchTaskRequest request, Pageable pageable) {
    // 1. Convert the DTO to filter criteria
    var criteria = request.toFilterCriteria();
    
    // 2. Build a Specification from the criteria (nulls are auto-skipped)
    Specification<Task> spec = TaskSpecification.withFilters(criteria);
    
    // 3. Execute the query with the dynamic specification + pagination
    Page<Task> taskPage = taskRepository.findAll(spec, pageable);
    Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
    return new PageResponse<>(responsePage);
}

// Simple text search (searches title and description)
public PageResponse<TaskResponse> searchTasksByQuery(String searchQuery, Pageable pageable) {
    Specification<Task> spec = TaskSpecification.withSearchQuery(searchQuery);
    Page<Task> taskPage = taskRepository.findAll(spec, pageable);
    Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
    return new PageResponse<>(responsePage);
}
```

### 6. Controller Endpoints

Two new endpoints -- POST for full advanced search, GET for simple keyword search:

**File:** `src/main/java/org/task/taskmaganer/controller/TaskController.java`

```java
// POST /tasks/search -- Advanced multi-criteria search
// Request body is optional; if empty, returns all tasks
@PostMapping("/search")
public ResponseEntity<PageResponse<TaskResponse>> searchTasks(
        @RequestBody(required = false) SearchTaskRequest request,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = createPageable(page, size, sort);
    SearchTaskRequest searchRequest = request != null ? request : new SearchTaskRequest();
    PageResponse<TaskResponse> response = taskService.searchTasks(searchRequest, pageable);
    return ResponseEntity.ok(response);
}

// GET /tasks/search?query=deploy -- Simple text search
@GetMapping("/search")
public ResponseEntity<PageResponse<TaskResponse>> searchTasksByQuery(
        @RequestParam String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = createPageable(page, size, sort);
    PageResponse<TaskResponse> response = taskService.searchTasksByQuery(query, pageable);
    return ResponseEntity.ok(response);
}
```

---

## What We Gained

| Before (Single Criterion) | After (Multi-Criterion with Specification) |
|---|---|
| One filter at a time | Combine any number of filters simultaneously |
| No text search | Full-text search across title and description |
| No date range queries | Filter by due date range and creation date range |
| 11 rigid endpoints for specific combos | 2 flexible endpoints that handle ALL combos |
| Adding a new filter = new endpoint | Adding a new filter = 1 small Specification method |
| No way to combine status + priority + user | Any combination: status + priority + user + date + text |

**Concrete example of what's now possible:**
> "Give me all **HIGH** priority, **IN_PROGRESS** tasks assigned to user **abc-123**, created **after 2026-01-01**, with **'deploy'** in the title, sorted by **due date ascending**, page **2** with **5** results per page."

One single POST request handles this. Before, we'd need a dedicated endpoint.

---

## Advantages

| Advantage | Explanation | Impact |
|---|---|---|
| **Dynamic Filtering** | Any combination of filters works without new code | No more method explosion (256 combos -> 1 method) |
| **Open/Closed Principle** | Add new filters without changing existing code | Just add a new `withXxx()` method and add it to `withFilters()` |
| **Null Safety** | Null parameters are silently ignored | No `NullPointerException`, no `if (param != null)` spaghetti |
| **Type Safety** | Compile-time checking on field names and types | Field name typos caught at compile time via `Task.Fields` constants |
| **Full-Text Search** | Case-insensitive search across multiple fields | Users can find tasks by keywords in title or description |
| **Date Ranges** | Supports from-only, to-only, or both | Flexible temporal queries without separate endpoints |
| **Pagination + Sorting** | Works seamlessly with Spring's `Pageable` | Consistent pagination across all search types |
| **Testability** | Each Specification can be unit tested independently | Small, isolated units of logic |

## Disadvantages

| Disadvantage | Explanation | Mitigation |
|---|---|---|
| **Learning Curve** | JPA Criteria API is verbose and unfamiliar to many | This document + good comments in code |
| **Verbose Code** | Criteria API syntax is more complex than `@Query` | Helper methods (`equalsPredicate`, `dateRangePredicate`) reduce repetition |
| **Debugging Difficulty** | Generated SQL is harder to trace back to Java code | Enable `spring.jpa.show-sql=true` and use EXPLAIN ANALYZE |
| **Limited Full-Text** | `LIKE %term%` doesn't use indexes efficiently | For large-scale search, migrate to Elasticsearch later |
| **No Aggregation** | Specification is for SELECT queries, not GROUP BY/SUM | Use `@Query` or native SQL for reporting |
| **N+1 Risk** | Eager fetching on `User` relation could cause extra queries | Use `@EntityGraph` or fetch joins if needed |

---

## Affected Files

| File | Change Type | Description |
|---|---|---|
| `specification/TaskSpecification.java` | **NEW** | Core Specification builder with all filter methods |
| `dto/request/SearchTaskRequest.java` | **NEW** | Request DTO for advanced search endpoint |
| `repository/TaskRepository.java` | **MODIFIED** | Added `extends JpaSpecificationExecutor<Task>` |
| `service/TaskService.java` | **MODIFIED** | Added `searchTasks()` and `searchTasksByQuery()` methods |
| `controller/TaskController.java` | **MODIFIED** | Added `POST /tasks/search` and `GET /tasks/search` endpoints |

---

## API Usage

### Example 1: Simple Text Search (GET)

Search for tasks containing "deploy" in the title or description:

```bash
curl -X GET "http://localhost:8080/tasks/search?query=deploy&page=0&size=10&sort=createdAt,desc"
```

**Response:**
```json
{
    "content": [
        {
            "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            "title": "Deploy to production",
            "description": "Deploy the new version of task manager to production server",
            "priority": "HIGH",
            "status": "IN_PROGRESS",
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "isActive": true,
            "dueDate": "2026-03-01T18:00:00",
            "createdAt": "2026-02-20T10:30:00",
            "updatedAt": "2026-02-25T14:20:00"
        }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
}
```

### Example 2: Advanced Search with Multiple Filters (POST)

Find all HIGH priority, IN_PROGRESS tasks for a specific user:

```bash
curl -X POST "http://localhost:8080/tasks/search?page=0&size=10&sort=dueDate,asc" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_PROGRESS",
    "priority": "HIGH",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "isActive": true
  }'
```

**Response:**
```json
{
    "content": [
        {
            "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
            "title": "Implement search feature",
            "description": "Add JPA Specification-based search and filtering",
            "priority": "HIGH",
            "status": "IN_PROGRESS",
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "isActive": true,
            "dueDate": "2026-02-28T23:59:59",
            "createdAt": "2026-02-15T09:00:00",
            "updatedAt": "2026-02-27T11:30:00"
        },
        {
            "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
            "title": "Deploy to production",
            "description": "Deploy v2.0 to production environment",
            "priority": "HIGH",
            "status": "IN_PROGRESS",
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "isActive": true,
            "dueDate": "2026-03-05T18:00:00",
            "createdAt": "2026-02-20T10:30:00",
            "updatedAt": "2026-02-26T16:45:00"
        }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 2,
    "totalPages": 1,
    "last": true
}
```

### Example 3: Date Range Search

Find tasks created in February 2026:

```bash
curl -X POST "http://localhost:8080/tasks/search?page=0&size=20" \
  -H "Content-Type: application/json" \
  -d '{
    "createdAtFrom": "2026-02-01T00:00:00",
    "createdAtTo": "2026-02-28T23:59:59"
  }'
```

**Response:**
```json
{
    "content": [
        {
            "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
            "title": "Implement search feature",
            "description": "Add JPA Specification-based search and filtering",
            "priority": "HIGH",
            "status": "IN_PROGRESS",
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "isActive": true,
            "dueDate": "2026-02-28T23:59:59",
            "createdAt": "2026-02-15T09:00:00",
            "updatedAt": "2026-02-27T11:30:00"
        }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
}
```

### Example 4: Full-Text Search + Filter Combo

Search for "deploy" in title/description AND filter to only COMPLETED tasks:

```bash
curl -X POST "http://localhost:8080/tasks/search?page=0&size=10&sort=updatedAt,desc" \
  -H "Content-Type: application/json" \
  -d '{
    "searchQuery": "deploy",
    "status": "COMPLETED",
    "isActive": true
  }'
```

**Response:**
```json
{
    "content": [
        {
            "id": "d4e5f6a7-b8c9-0123-defa-234567890123",
            "title": "Deploy hotfix v1.5.2",
            "description": "Deploy critical security hotfix to production",
            "priority": "HIGH",
            "status": "COMPLETED",
            "userId": "660e8400-e29b-41d4-a716-446655440001",
            "isActive": true,
            "dueDate": "2026-02-10T12:00:00",
            "createdAt": "2026-02-08T08:00:00",
            "updatedAt": "2026-02-10T11:45:00"
        }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
}
```

### Example 5: Empty Search (Returns All Tasks)

Send an empty body to get all tasks (with pagination):

```bash
curl -X POST "http://localhost:8080/tasks/search?page=0&size=5&sort=createdAt,desc" \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Response:**
```json
{
    "content": [
        {
            "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
            "title": "Implement search feature",
            "description": "Add JPA Specification-based search and filtering",
            "priority": "HIGH",
            "status": "IN_PROGRESS",
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "isActive": true,
            "dueDate": "2026-02-28T23:59:59",
            "createdAt": "2026-02-15T09:00:00",
            "updatedAt": "2026-02-27T11:30:00"
        },
        {
            "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
            "title": "Write unit tests",
            "description": "Add comprehensive tests for TaskService",
            "priority": "MEDIUM",
            "status": "PENDING",
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "isActive": true,
            "dueDate": "2026-03-10T18:00:00",
            "createdAt": "2026-02-14T11:00:00",
            "updatedAt": "2026-02-14T11:00:00"
        }
    ],
    "pageNumber": 0,
    "pageSize": 5,
    "totalElements": 15,
    "totalPages": 3,
    "last": false
}
```

### Example 6: Due Date Range + Priority

Find tasks due this week with MEDIUM or higher priority:

```bash
curl -X POST "http://localhost:8080/tasks/search?page=0&size=10" \
  -H "Content-Type: application/json" \
  -d '{
    "priority": "MEDIUM",
    "dueDateFrom": "2026-02-24T00:00:00",
    "dueDateTo": "2026-03-02T23:59:59",
    "isActive": true
  }'
```

---

## Query Parameters Reference

These are URL query parameters, used for **pagination and sorting** on both endpoints:

| Parameter | Type | Default | Description | Example |
|---|---|---|---|---|
| `page` | `int` | `0` | Page number (0-indexed). Page 0 = first page. | `?page=2` |
| `size` | `int` | `10` | Number of results per page | `?size=25` |
| `sort` | `String` | `createdAt,desc` | Sort field and direction, comma-separated | `?sort=dueDate,asc` |
| `query` | `String` | *(required for GET only)* | Search text for GET /tasks/search | `?query=deploy` |

**Available sort fields:** `createdAt`, `updatedAt`, `dueDate`, `title`, `status`, `priority`

---

## Request Body Fields

These are JSON body fields for `POST /tasks/search`. **All fields are optional.** If omitted (or `null`), that filter is skipped.

| Field | Type | Description | Example Values |
|---|---|---|---|
| `searchQuery` | `String` | Case-insensitive text search in title AND description | `"deploy"`, `"bug fix"` |
| `status` | `TaskStatus` | Exact match on task status | `"PENDING"`, `"IN_PROGRESS"`, `"COMPLETED"`, `"CANCELLED"` |
| `priority` | `TaskPriority` | Exact match on task priority | `"LOW"`, `"MEDIUM"`, `"HIGH"` |
| `userId` | `UUID` | Filter tasks assigned to a specific user | `"550e8400-e29b-41d4-a716-446655440000"` |
| `isActive` | `Boolean` | Filter by active/inactive status | `true`, `false` |
| `dueDateFrom` | `LocalDateTime` | Due date >= this value | `"2026-01-01T00:00:00"` |
| `dueDateTo` | `LocalDateTime` | Due date <= this value | `"2026-12-31T23:59:59"` |
| `createdAtFrom` | `LocalDateTime` | Created at >= this value | `"2026-02-01T00:00:00"` |
| `createdAtTo` | `LocalDateTime` | Created at <= this value | `"2026-02-28T23:59:59"` |

---

## Decision Log

### Why Specification, not QueryDSL?

| Factor | JPA Specification | QueryDSL |
|---|---|---|
| **Dependencies** | Built into Spring Data JPA -- zero extra dependencies | Requires `querydsl-jpa`, `querydsl-apt`, annotation processor setup |
| **Build complexity** | None | Requires code generation step (`Q` classes), plugin configuration |
| **Learning curve** | Moderate -- standard JPA Criteria API | Higher -- new DSL syntax to learn |
| **Our use case** | 8 optional filters -- Specification handles this perfectly | Overkill for this complexity level |
| **Maintenance** | Just Java code, no generated files | Generated `Q` classes can break on entity changes |

**Decision:** Specification is sufficient for our complexity level. If we eventually need 50+ filters with nested joins and subqueries, we'd reconsider QueryDSL.

### Why Both POST and GET for Search?

| Endpoint | Method | Use Case |
|---|---|---|
| `GET /tasks/search?query=deploy` | GET | Simple text search from a search bar. Bookmarkable, cacheable. |
| `POST /tasks/search` | POST | Complex multi-criteria search. Request body allows structured, nested filter objects. |

**Why not only GET with query params?** Because date ranges, UUIDs, and enum values become unwieldy in URLs. A JSON body is cleaner:

```
# Ugly URL (GET with many params):
GET /tasks/search?status=IN_PROGRESS&priority=HIGH&userId=550e8400-...&dueDateFrom=2026-01-01T00:00:00&dueDateTo=2026-12-31T23:59:59

# Clean JSON body (POST):
POST /tasks/search
{ "status": "IN_PROGRESS", "priority": "HIGH", "userId": "550e8400-...", ... }
```

### Why Keep Old Endpoints?

The old endpoints (`/tasks/status/{status}`, `/tasks/priority/{priority}`, etc.) are **still there**. We didn't remove them because:

1. **Backward compatibility** -- existing clients depend on them
2. **Simplicity** -- `GET /tasks/status/PENDING` is simpler than `POST /tasks/search` with a body for basic cases
3. **Discoverability** -- REST paths are self-documenting
4. **Gradual migration** -- new features use `/search`, old code migrates at its own pace

---

## Performance Notes

### Recommended Database Indexes

The search uses `LIKE '%term%'` for text search and equality checks for enum fields. Without indexes, every query does a full table scan.

```sql
-- Index for status filtering (most common filter)
CREATE INDEX idx_tasks_status ON tasks (status);

-- Index for priority filtering
CREATE INDEX idx_tasks_priority ON tasks (priority);

-- Index for user lookups
CREATE INDEX idx_tasks_user_id ON tasks (user_id);

-- Index for active task filtering (used in almost every query)
CREATE INDEX idx_tasks_is_active ON tasks (is_active);

-- Composite index for the most common combination: active + status
CREATE INDEX idx_tasks_active_status ON tasks (is_active, status);

-- Index for date range queries
CREATE INDEX idx_tasks_due_date ON tasks (due_date);
CREATE INDEX idx_tasks_created_at ON tasks (created_at);

-- For text search: trigram index (PostgreSQL only, requires pg_trgm extension)
-- This makes LIKE '%term%' use an index instead of full table scan
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_tasks_title_trgm ON tasks USING gin (title gin_trgm_ops);
CREATE INDEX idx_tasks_description_trgm ON tasks USING gin (description gin_trgm_ops);
```

### Analyzing Query Performance

Use `EXPLAIN ANALYZE` to check if your indexes are being used:

```sql
EXPLAIN ANALYZE
SELECT * FROM tasks
WHERE status = 'IN_PROGRESS'
  AND priority = 'HIGH'
  AND is_active = true
  AND created_at >= '2026-01-01'
ORDER BY created_at DESC
LIMIT 10;
```

Look for:
- **Index Scan** or **Bitmap Index Scan** = index is being used (good)
- **Seq Scan** = full table scan, no index used (bad for large tables)

### Performance Tips

1. **Enable SQL logging during development** to see what Hibernate generates:
   ```properties
   spring.jpa.show-sql=true
   spring.jpa.properties.hibernate.format_sql=true
   ```

2. **Watch for N+1 queries** -- the `User` relation on Task uses `FetchType.EAGER`, which means every Task result also fetches its User. For large result sets, consider `@EntityGraph` or switching to `FetchType.LAZY`.

3. **LIKE '%term%' cannot use B-tree indexes.** For tables with 100k+ rows, consider PostgreSQL's `pg_trgm` extension or migrating text search to Elasticsearch.

---

## Future Improvements

1. **Elasticsearch Integration** -- For large-scale full-text search with relevance scoring, fuzzy matching, and highlighting
2. **Saved Searches** -- Let users save their filter combinations and reuse them
3. **Search History** -- Track what users search for (analytics + autocomplete)
4. **Sorting by Multiple Fields** -- Currently single-field sort; extend to `sort=priority,desc&sort=dueDate,asc`
5. **Filter by Multiple Values** -- `status=IN_PROGRESS,PENDING` (IN clause instead of equals)
6. **Export Search Results** -- CSV/Excel export of filtered results
7. **Search Suggestions** -- Autocomplete based on existing task titles
8. **Caching** -- Cache frequently used filter combinations with Spring Cache

---

## Test Scenarios

### Test Case 1: Search by Text Only

**Input:** `searchQuery = "deploy"`
**Expected:** All tasks where title OR description contains "deploy" (case-insensitive)
**SQL Equivalent:** `WHERE LOWER(title) LIKE '%deploy%' OR LOWER(description) LIKE '%deploy%'`

### Test Case 2: Filter by Status + Priority

**Input:** `status = IN_PROGRESS`, `priority = HIGH`
**Expected:** Only tasks that are both IN_PROGRESS and HIGH priority
**SQL Equivalent:** `WHERE status = 'IN_PROGRESS' AND priority = 'HIGH'`

### Test Case 3: Date Range with Single Bound

**Input:** `createdAtFrom = "2026-02-01T00:00:00"` (no `createdAtTo`)
**Expected:** All tasks created on or after Feb 1, 2026 (no upper bound)
**SQL Equivalent:** `WHERE created_at >= '2026-02-01 00:00:00'`

### Test Case 4: All Filters Combined

**Input:** All 9 fields populated
**Expected:** Tasks matching ALL criteria simultaneously (AND logic)
**SQL Equivalent:** `WHERE (LOWER(title) LIKE '%term%' OR LOWER(description) LIKE '%term%') AND status = 'X' AND priority = 'Y' AND user_id = 'Z' AND is_active = true AND due_date >= 'A' AND due_date <= 'B' AND created_at >= 'C' AND created_at <= 'D'`

### Test Case 5: Empty Request Body

**Input:** `{}` (all fields null)
**Expected:** Returns ALL tasks (no filters applied), equivalent to `findAll()`
**Why:** Every null field returns `cb.conjunction()` (always true), so the combined WHERE clause is `WHERE 1=1 AND 1=1 AND ...` = no filtering

### Test Case 6: No Results

**Input:** `status = COMPLETED`, `priority = LOW`, `searchQuery = "xyznonexistent"`
**Expected:** Empty content array, `totalElements = 0`
**Response:** `{ "content": [], "totalElements": 0, "totalPages": 0, "last": true }`

---

## Error Scenarios

### Invalid Enum Value

**Request:**
```json
{ "status": "INVALID_STATUS" }
```

**Response (400 Bad Request):**
```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "JSON parse error: Cannot deserialize value of type `TaskStatus` from String \"INVALID_STATUS\": not one of the values accepted for Enum class: [PENDING, IN_PROGRESS, COMPLETED, CANCELLED]"
}
```

### Invalid UUID Format

**Request:**
```json
{ "userId": "not-a-valid-uuid" }
```

**Response (400 Bad Request):**
```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "JSON parse error: Cannot deserialize value of type `java.util.UUID` from String \"not-a-valid-uuid\": UUID has to be represented by standard 36-char representation"
}
```

### Invalid Date Format

**Request:**
```json
{ "dueDateFrom": "27-02-2026" }
```

**Response (400 Bad Request):**
```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "JSON parse error: Cannot deserialize value of type `java.time.LocalDateTime` from String \"27-02-2026\": expected format \"yyyy-MM-dd'T'HH:mm:ss\""
}
```

**Correct format:** `"2026-02-27T00:00:00"` (ISO 8601)

---

## Conclusion

The JPA Specification pattern transforms our filtering from a rigid, endpoint-per-combination approach into a flexible, composable system. The core trade is straightforward:

- **Before:** 11 endpoints covering ~9 out of 256 possible filter combinations. Adding one new filter = potentially doubling the number of endpoints.
- **After:** 2 endpoints covering ALL 256+ combinations. Adding one new filter = 1 new Specification method (about 5 lines of code).

The key design principle: **each Specification is a small, independent, reusable building block**. They know nothing about each other. The `withFilters()` method snaps them together. Null parameters are invisible. The result is a system that's both more powerful and simpler to maintain.
