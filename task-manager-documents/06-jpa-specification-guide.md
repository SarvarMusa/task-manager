# JPA Specification Guide - What, When, How?

## Date: 2026-02-27

---

## What is Specification?

### The Simple Explanation

Think of Specifications as **LEGO blocks for database queries**. Each block is a tiny, simple filter:

```
Block 1: color = red
Block 2: size = large  
Block 3: price < $50
```

You snap them together to build exactly the query you need:

```
Block 1 + Block 2           → red AND large
Block 1 + Block 3           → red AND price < $50
Block 1 + Block 2 + Block 3 → red AND large AND price < $50
Block 2 only                → large
Nothing                     → give me everything
```

The beauty: you write each block **once**, and combine them in **any order, any combination, at runtime**. You don't need to pre-build every possible combination.

### What It Actually Is (Technically)

`Specification<T>` is a Spring Data JPA interface with **one method**:

```java
@FunctionalInterface
public interface Specification<T> {
    Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb);
}
```

That's it. A Specification is just a function that takes three JPA Criteria API objects and returns a `Predicate` (a WHERE clause condition).

Here's a real Specification and the SQL it produces:

```java
// Java Specification:
Specification<Task> highPriority = (root, query, cb) -> 
    cb.equal(root.get("priority"), TaskPriority.HIGH);

// SQL equivalent:
// WHERE priority = 'HIGH'
```

```java
// Java: Combining two Specifications
Specification<Task> combined = highPriority.and(inProgress);

// SQL equivalent:
// WHERE priority = 'HIGH' AND status = 'IN_PROGRESS'
```

```java
// Java: Three Specifications combined with mixed AND/OR
Specification<Task> complex = highPriority.and(inProgress).or(overdue);

// SQL equivalent:
// WHERE (priority = 'HIGH' AND status = 'IN_PROGRESS') OR due_date < NOW()
```

### The Translation Table

Here's how common SQL operations map to Specification code:

| SQL | CriteriaBuilder Method | Java Code |
|---|---|---|
| `WHERE status = 'HIGH'` | `cb.equal()` | `cb.equal(root.get("status"), "HIGH")` |
| `WHERE status != 'CANCELLED'` | `cb.notEqual()` | `cb.notEqual(root.get("status"), "CANCELLED")` |
| `WHERE title LIKE '%deploy%'` | `cb.like()` | `cb.like(root.get("title"), "%deploy%")` |
| `WHERE price > 100` | `cb.greaterThan()` | `cb.greaterThan(root.get("price"), 100)` |
| `WHERE price >= 100` | `cb.greaterThanOrEqualTo()` | `cb.greaterThanOrEqualTo(root.get("price"), 100)` |
| `WHERE price BETWEEN 50 AND 100` | `cb.between()` | `cb.between(root.get("price"), 50, 100)` |
| `WHERE status IS NULL` | `cb.isNull()` | `cb.isNull(root.get("status"))` |
| `WHERE status IS NOT NULL` | `cb.isNotNull()` | `cb.isNotNull(root.get("status"))` |
| `WHERE a AND b` | `cb.and()` | `cb.and(predicateA, predicateB)` |
| `WHERE a OR b` | `cb.or()` | `cb.or(predicateA, predicateB)` |
| `WHERE NOT a` | `cb.not()` | `cb.not(predicateA)` |
| `1 = 1` (always true / no-op) | `cb.conjunction()` | `cb.conjunction()` |
| `1 = 0` (always false) | `cb.disjunction()` | `cb.disjunction()` |

---

## When Should You Use It?

### Use Specification

| Situation | Why Specification Fits | Example |
|---|---|---|
| **Dynamic filters** -- the user picks which filters to apply | Each filter is a separate Specification; combine only the ones the user provides | Task search: user may filter by status, priority, date, text -- any combination |
| **Optional criteria** -- some filters may be null | Null Specifications return `conjunction()` (no-op), so they're silently skipped | Admin panel where any field can be left empty |
| **Date range queries** -- from and/or to boundaries | Date range Specifications handle from-only, to-only, or both gracefully | "Show tasks created between Jan 1 and Mar 31" |
| **Complex AND/OR combinations** -- building WHERE clauses dynamically | `.and()` and `.or()` compose naturally | "Status is PENDING or IN_PROGRESS AND priority is HIGH" |
| **Runtime-determined queries** -- you don't know the query shape at compile time | Specifications are built at runtime based on user input | Search forms, filter sidebars, report builders |
| **Multiple search fields** -- searching across title, description, etc. | OR-combine multiple LIKE predicates in one Specification | "Search for 'deploy' in both title and description" |
| **Growing filter requirements** -- new filters added frequently | Adding a filter = adding one small method, no changes to existing code | "We now need to filter by assignee team" -- 5 lines of new code |

### Don't Use Specification

| Situation | Why Not | Better Alternative |
|---|---|---|
| **Fixed, known queries** -- the query never changes | Specification adds unnecessary abstraction | `@Query("SELECT t FROM Task t WHERE t.status = :status")` |
| **Single criterion** -- always filtering by exactly one field | A derived query method is simpler | `List<Task> findByStatus(TaskStatus status)` |
| **Native SQL required** -- database-specific features | Criteria API doesn't support database-specific syntax | `@Query(value = "SELECT ... FROM ...", nativeQuery = true)` |
| **Aggregation queries** -- GROUP BY, SUM, COUNT, AVG | Criteria API for aggregation is extremely verbose and painful | `@Query` with JPQL or native SQL |
| **Full-text search at scale** -- millions of rows, relevance scoring | `LIKE '%term%'` can't use regular indexes and doesn't provide relevance | Elasticsearch, PostgreSQL full-text search (`tsvector`) |
| **Simple CRUD** -- basic findById, save, delete | Specification is overkill | Standard `JpaRepository` methods |
| **Complex joins across 5+ tables** -- deeply nested relationships | Criteria API joins become unreadable | QueryDSL or native SQL |
| **Batch operations** -- UPDATE/DELETE multiple rows | Specification is for SELECT queries only | `@Modifying @Query` |

---

## What Problems Does It Solve?

### Problem 1: Method Explosion

When you have multiple optional filters, you end up writing a separate method for every combination.

**Before (without Specification):**

```java
// TaskRepository -- a new method for every filter combination
public interface TaskRepository extends JpaRepository<Task, UUID> {
    
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    Page<Task> findByPriority(TaskPriority priority, Pageable pageable);
    Page<Task> findByUserId(UUID userId, Pageable pageable);
    Page<Task> findByStatusAndPriority(TaskStatus status, TaskPriority priority, Pageable pageable);
    Page<Task> findByStatusAndUserId(TaskStatus status, UUID userId, Pageable pageable);
    Page<Task> findByPriorityAndUserId(TaskPriority priority, UUID userId, Pageable pageable);
    Page<Task> findByStatusAndPriorityAndUserId(TaskStatus s, TaskPriority p, UUID u, Pageable pageable);
    Page<Task> findByIsActiveTrue(Pageable pageable);
    Page<Task> findByIsActiveTrueAndStatus(TaskStatus status, Pageable pageable);
    Page<Task> findByIsActiveTrueAndPriority(TaskPriority priority, Pageable pageable);
    Page<Task> findByIsActiveTrueAndStatusAndPriority(TaskStatus s, TaskPriority p, Pageable pageable);
    // ... 3 filters = 8 combinations. 8 filters = 256 combinations!
    // Nobody wants to maintain 256 methods.
}
```

**After (with Specification):**

```java
public interface TaskRepository extends JpaRepository<Task, UUID>,
                                        JpaSpecificationExecutor<Task> {
    // That's it. ONE interface extension.
    // findAll(Specification<Task> spec, Pageable pageable) is inherited.
    // It handles ALL 256 combinations.
}
```

```java
// Each filter is a tiny method (5 lines each):
public static Specification<Task> withStatus(TaskStatus status) { ... }
public static Specification<Task> withPriority(TaskPriority priority) { ... }
public static Specification<Task> withUserId(UUID userId) { ... }
// + 1 composite method that combines them:
public static Specification<Task> withFilters(TaskFilterCriteria criteria) { ... }
```

**Result:** 8 small methods + 1 composite = handles 256 combinations.

---

### Problem 2: Null Check Chaos

Without Specification, handling optional parameters requires ugly null checks.

**Before (with @Query):**

```java
// This JPQL query is a nightmare to read and maintain:
@Query("SELECT t FROM Task t WHERE " +
       "(:status IS NULL OR t.status = :status) AND " +
       "(:priority IS NULL OR t.priority = :priority) AND " +
       "(:userId IS NULL OR t.user.id = :userId) AND " +
       "(:isActive IS NULL OR t.isActive = :isActive)")
Page<Task> findByOptionalCriteria(
    @Param("status") TaskStatus status,
    @Param("priority") TaskPriority priority,
    @Param("userId") UUID userId,
    @Param("isActive") Boolean isActive,
    Pageable pageable);

// Problems:
// 1. Adding a new filter = modifying this already-long query string
// 2. Date ranges make it even worse (two params per date field)
// 3. Text search with LIKE adds more complexity
// 4. Easy to introduce typos in the string (no compile-time checking)
// 5. Some databases handle "IS NULL OR" differently (portability issues)
```

**After (with Specification):**

```java
// Each filter handles its own null checking internally:
public static Specification<Task> withStatus(TaskStatus status) {
    return (root, query, cb) ->
        status == null 
            ? cb.conjunction()                       // null = skip this filter
            : cb.equal(root.get("status"), status);  // not null = apply filter
}

// The composite just chains them -- no null checks needed here:
public static Specification<Task> withFilters(TaskFilterCriteria criteria) {
    return Specification.where(
        withStatus(criteria.status())        // handles its own nulls
            .and(withPriority(criteria.priority()))  // handles its own nulls
            .and(withUserId(criteria.userId()))       // handles its own nulls
    );
}
```

**Result:** Each filter encapsulates its own null-handling. The composite method is clean and readable.

---

### Problem 3: Dynamic AND/OR Combination

Building queries dynamically with string concatenation is dangerous and messy.

**Before (string concatenation -- DO NOT DO THIS):**

```java
// DANGEROUS: SQL injection risk + unmaintainable
public List<Task> searchTasks(String status, String priority, String keyword) {
    StringBuilder sql = new StringBuilder("SELECT * FROM tasks WHERE 1=1");
    
    if (status != null) {
        sql.append(" AND status = '").append(status).append("'");  // SQL INJECTION!
    }
    if (priority != null) {
        sql.append(" AND priority = '").append(priority).append("'");  // SQL INJECTION!
    }
    if (keyword != null) {
        sql.append(" AND (title LIKE '%").append(keyword).append("%'");  // SQL INJECTION!
        sql.append(" OR description LIKE '%").append(keyword).append("%')");
    }
    
    return entityManager.createNativeQuery(sql.toString(), Task.class).getResultList();
}
// This code is vulnerable, untyped, and will break silently with typos.
```

**After (with Specification -- type-safe, injection-proof):**

```java
// Type-safe. No string concatenation. No SQL injection possible.
public static Specification<Task> withSearchQuery(String searchQuery) {
    return (root, query, cb) -> {
        if (searchQuery == null || searchQuery.isBlank()) {
            return cb.conjunction();
        }
        String pattern = "%" + searchQuery.toLowerCase().trim() + "%";
        return cb.or(
            cb.like(cb.lower(root.get(Task.Fields.title)), pattern),       // type-safe field reference
            cb.like(cb.lower(root.get(Task.Fields.description)), pattern)  // type-safe field reference
        );
    };
}

// Combine with other specs:
Specification<Task> spec = withSearchQuery("deploy")
    .and(withStatus(TaskStatus.IN_PROGRESS))
    .and(withPriority(TaskPriority.HIGH));

// JPA generates a parameterized query -- SQL injection is impossible.
```

**Result:** Type safety, no injection risk, clean composition.

---

### Problem 4: Date Range and Complex Criteria

Date ranges require two parameters (from and to), and either can be null. This gets messy fast without Specification.

**Before (messy conditional logic):**

```java
// In service layer -- ugly conditional branching:
public Page<Task> searchByDateRange(LocalDateTime from, LocalDateTime to, Pageable p) {
    if (from != null && to != null) {
        return taskRepository.findByCreatedAtBetween(from, to, p);
    } else if (from != null) {
        return taskRepository.findByCreatedAtGreaterThanOrEqualTo(from, p);
    } else if (to != null) {
        return taskRepository.findByCreatedAtLessThanOrEqualTo(to, p);
    } else {
        return taskRepository.findAll(p);
    }
}
// Now imagine combining this with status, priority, userId...
// The if/else tree becomes exponentially complex.
```

**After (with Specification -- handles all date range variants):**

```java
// One method handles all 4 cases: both bounds, from only, to only, neither
public static Specification<Task> withCreatedAtBetween(LocalDateTime from, LocalDateTime to) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null)   predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    };
}

// And it composes naturally with everything else:
Specification<Task> spec = withCreatedAtBetween(from, to)
    .and(withStatus(TaskStatus.IN_PROGRESS))
    .and(withPriority(TaskPriority.HIGH));
// All null handling is automatic. No if/else trees.
```

**Result:** Clean `.and()` chains replace exponential if/else branching.

---

## Real-World Scenarios

### Scenario 1: E-Commerce Product Filtering

Imagine you're building the product listing page for an online store. Users can filter by:

- **Category** (Electronics, Clothing, Books...)
- **Price range** ($0-$50, $50-$100, custom range)
- **Brand** (Apple, Samsung, Sony...)
- **In stock only** (yes/no)
- **Rating** (4+ stars)
- **Search text** (product name, description)

That's 6 optional filters = 2^6 = **64 combinations**.

```java
public class ProductSpecification {
    
    public static Specification<Product> withFilters(ProductFilterCriteria c) {
        return Specification.where(
            withCategory(c.category())
                .and(withPriceRange(c.minPrice(), c.maxPrice()))
                .and(withBrand(c.brand()))
                .and(withInStock(c.inStockOnly()))
                .and(withMinRating(c.minRating()))
                .and(withSearchText(c.searchText()))
        );
    }
    
    public static Specification<Product> withCategory(String category) {
        return (root, query, cb) -> category == null 
            ? cb.conjunction() 
            : cb.equal(root.get("category"), category);
    }
    
    public static Specification<Product> withPriceRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (min != null) preds.add(cb.greaterThanOrEqualTo(root.get("price"), min));
            if (max != null) preds.add(cb.lessThanOrEqualTo(root.get("price"), max));
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
    
    // ... similar for brand, inStock, minRating, searchText
}
```

**Result:** 6 small methods handle all 64 filter combinations.

---

### Scenario 2: Admin Panel / Log Viewer

An admin dashboard where you search through application logs:

- **Date range** (from/to)
- **Log level** (ERROR, WARN, INFO, DEBUG)
- **User who triggered the action**
- **Message text search**
- **Module/service name**

```java
public class AuditLogSpecification {
    
    public static Specification<AuditLog> withFilters(LogFilterCriteria c) {
        return Specification.where(
            withDateRange(c.from(), c.to())
                .and(withLevel(c.level()))
                .and(withUser(c.userId()))
                .and(withMessageSearch(c.searchText()))
                .and(withModule(c.module()))
        );
    }
    
    // Each spec method follows the same pattern:
    // - Null check → conjunction() (skip)
    // - Value present → create predicate
}
```

**Real SQL generated when admin filters by ERROR level + date range:**
```sql
SELECT * FROM audit_logs 
WHERE level = 'ERROR' 
  AND created_at >= '2026-02-01 00:00:00' 
  AND created_at <= '2026-02-28 23:59:59'
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

---

### Scenario 3: Task Manager (This Project)

Our project has **8 optional filter fields** for tasks:

| Filter | Field | Type |
|---|---|---|
| Text search | title, description | `LIKE '%term%'` |
| Status | status | `= ENUM` |
| Priority | priority | `= ENUM` |
| User | user_id | `= UUID` |
| Active | is_active | `= BOOLEAN` |
| Due date from | due_date | `>= DATETIME` |
| Due date to | due_date | `<= DATETIME` |
| Created from | created_at | `>= DATETIME` |
| Created to | created_at | `<= DATETIME` |

8 optional filters = 2^8 = **256 possible combinations**, all handled by **8 small Specification methods + 1 composite `withFilters()` method**.

The actual files in our project:
- `TaskSpecification.java` -- 8 filter methods + helpers (~235 lines)
- `SearchTaskRequest.java` -- DTO with 9 optional fields (~132 lines)
- Repository: added `JpaSpecificationExecutor<Task>` (1 line change)
- Service: 2 new methods (~15 lines)
- Controller: 2 new endpoints (~30 lines)

**Total new code:** ~400 lines covering 256 filter combinations. Without Specification, covering even 20 combinations would require more code than that.

---

## Specification vs Alternatives

| Feature | Specification | @Query (JPQL) | Derived Query Methods | Native SQL | Elasticsearch |
|---|---|---|---|---|---|
| **Dynamic filters** | Excellent -- built for this | Poor -- static query strings | Impossible -- method name is the query | Poor -- string concatenation | Excellent |
| **Type safety** | Good -- Criteria API is typed | Poor -- strings, no compile check | Good -- method name checked at startup | None -- raw SQL | N/A (JSON queries) |
| **Readability** | Moderate -- verbose but structured | Good for simple queries | Excellent for 1-2 params | Depends | Good (JSON DSL) |
| **Null handling** | Built-in (conjunction pattern) | Manual (`IS NULL OR`) | Not supported | Manual | Built-in |
| **Learning curve** | Medium -- Criteria API is unfamiliar | Low -- just write JPQL | Very low -- method naming convention | Low -- just SQL | High -- separate system |
| **Performance** | Same as JPQL (generates same SQL) | Good | Good | Best (direct DB) | Best for text search |
| **Aggregation** | Possible but very verbose | Good | Very limited | Best | Good |
| **Full-text search** | Basic (LIKE only) | Basic (LIKE only) | Basic | DB-specific features | Excellent |
| **Maintenance** | Easy to add/remove filters | Query string grows linearly | Method count grows exponentially | Query string grows | Separate infrastructure |
| **Dependencies** | Spring Data JPA (already there) | Spring Data JPA | Spring Data JPA | Spring Data JPA | Elasticsearch + Spring Data ES |
| **Best for** | 3+ optional filters | Fixed queries, simple joins | 1-2 fixed parameters | Complex DB-specific queries | Large-scale text search |

**Bottom line:** Use Specification when you have **3 or more optional filter parameters**. Below that, simpler alternatives are fine.

---

## Best Practices

### Practice 1: Single Responsibility per Specification

Each Specification method should filter by **one thing only**. Don't combine multiple concerns in a single method.

**DON'T:**
```java
// BAD: This method does two unrelated things
public static Specification<Task> withStatusAndPriority(TaskStatus status, TaskPriority priority) {
    return (root, query, cb) -> {
        Predicate statusPred = cb.equal(root.get("status"), status);
        Predicate priorityPred = cb.equal(root.get("priority"), priority);
        return cb.and(statusPred, priorityPred);
    };
}
// What if the user only wants to filter by status? Can't use this method.
```

**DO:**
```java
// GOOD: Each method does one thing
public static Specification<Task> withStatus(TaskStatus status) {
    return (root, query, cb) -> status == null 
        ? cb.conjunction() 
        : cb.equal(root.get("status"), status);
}

public static Specification<Task> withPriority(TaskPriority priority) {
    return (root, query, cb) -> priority == null 
        ? cb.conjunction() 
        : cb.equal(root.get("priority"), priority);
}

// Combine them when needed:
Specification<Task> spec = withStatus(status).and(withPriority(priority));
```

**Why:** Single-responsibility specs are reusable. You can combine `withStatus` with anything, not just `withPriority`.

---

### Practice 2: Use a Composite Specification Method

Create a single `withFilters()` method that combines all individual specs. This is the one method the service layer calls.

**DON'T:**
```java
// BAD: Service layer manually combining specs -- messy and error-prone
public PageResponse<TaskResponse> searchTasks(SearchTaskRequest req, Pageable p) {
    Specification<Task> spec = Specification.where(null);
    if (req.getStatus() != null) spec = spec.and(TaskSpecification.withStatus(req.getStatus()));
    if (req.getPriority() != null) spec = spec.and(TaskSpecification.withPriority(req.getPriority()));
    if (req.getUserId() != null) spec = spec.and(TaskSpecification.withUserId(req.getUserId()));
    // ... repeats for every field
    // The service layer knows too much about Specification internals.
}
```

**DO:**
```java
// GOOD: One composite method in TaskSpecification
public static Specification<Task> withFilters(TaskFilterCriteria criteria) {
    return Specification.where(
        withSearchQuery(criteria.searchQuery())
            .and(withStatus(criteria.status()))
            .and(withPriority(criteria.priority()))
            .and(withUserId(criteria.userId()))
            .and(withIsActive(criteria.isActive()))
            .and(withDueDateBetween(criteria.dueDateFrom(), criteria.dueDateTo()))
            .and(withCreatedAtBetween(criteria.createdAtFrom(), criteria.createdAtTo()))
    );
}

// Service layer is clean:
public PageResponse<TaskResponse> searchTasks(SearchTaskRequest req, Pageable p) {
    var criteria = req.toFilterCriteria();
    Specification<Task> spec = TaskSpecification.withFilters(criteria);
    Page<Task> page = taskRepository.findAll(spec, p);
    return new PageResponse<>(page.map(TaskResponse::new));
}
```

**Why:** The service layer doesn't need to know about Specification internals. The composite method is the single entry point.

---

### Practice 3: Null Handling with the Conjunction Pattern

When a parameter is null (meaning "don't filter by this"), return `cb.conjunction()` -- which means "always true" in SQL (`1=1`). This makes null parameters invisible in the final query.

**DON'T:**
```java
// BAD: Returning null from a Specification causes NullPointerException
public static Specification<Task> withStatus(TaskStatus status) {
    if (status == null) return null;  // THIS WILL BREAK when .and() is called on it!
    return (root, query, cb) -> cb.equal(root.get("status"), status);
}
```

**DO:**
```java
// GOOD: Return conjunction() for null values -- it's a no-op in SQL
public static Specification<Task> withStatus(TaskStatus status) {
    return (root, query, cb) -> status == null 
        ? cb.conjunction()                          // SQL: 1=1 (always true, ignored by optimizer)
        : cb.equal(root.get("status"), status);     // SQL: status = 'IN_PROGRESS'
}
```

**How conjunction works in the final SQL:**
```sql
-- If status=IN_PROGRESS, priority=null, userId=null:
WHERE status = 'IN_PROGRESS' AND 1=1 AND 1=1
-- The database optimizer simplifies this to:
WHERE status = 'IN_PROGRESS'
-- The 1=1 conditions have zero performance impact.
```

---

### Practice 4: Type Safety with Field Constants

Never use string literals for field names. Use constants or metamodel classes. A typo in a string won't be caught until runtime.

**DON'T:**
```java
// BAD: String literal -- typo won't be caught until the query runs
public static Specification<Task> withStatus(TaskStatus status) {
    return (root, query, cb) -> cb.equal(root.get("statsu"), status);  // typo! "statsu" not "status"
    // This compiles fine but throws an exception at runtime.
}
```

**DO:**
```java
// GOOD: Using the Task.Fields constants class
public static Specification<Task> withStatus(TaskStatus status) {
    return (root, query, cb) -> cb.equal(root.get(Task.Fields.status), status);
    // If you typo "Task.Fields.statsu", the compiler catches it immediately.
}

// The Fields class in the entity:
@Entity
public class Task {
    // ... fields ...
    
    public static final class Fields {
        public static final String id = "id";
        public static final String title = "title";
        public static final String description = "description";
        public static final String priority = "priority";
        public static final String status = "status";
        public static final String user = "user";
        public static final String dueDate = "dueDate";
        public static final String isActive = "isActive";
        public static final String createdAt = "createdAt";
        public static final String updatedAt = "updatedAt";
    }
}
```

**Even better:** Use the JPA Metamodel (generated `Task_` class) for full compile-time type safety on both field name AND field type. But for most projects, a `Fields` constants class is a good balance.

---

## Decision Tree

When you're not sure whether to use Specification, walk through this:

```
START: How many filter parameters does your query have?
│
├── 0-1 parameters (fixed query)
│   └── Use @Query or derived query methods
│       Example: findByStatus(TaskStatus status)
│
├── 2 parameters (both always required)
│   └── Use @Query or derived query methods
│       Example: findByStatusAndPriority(status, priority)
│
├── 2 parameters (one or both OPTIONAL)
│   └── SPECIFICATION ✅
│       Reason: Optional params = null handling = conjunction pattern
│
├── 3+ parameters (any optionality)
│   └── SPECIFICATION ✅
│       Reason: 3 optional params = 8 combinations, and it only grows from here
│
└── Special cases:
    │
    ├── Need aggregation (GROUP BY, SUM, COUNT)?
    │   └── Use @Query (JPQL or native SQL)
    │
    ├── Need database-specific features?
    │   └── Use native SQL with @Query
    │
    ├── Need full-text search at scale (millions of rows)?
    │   └── Use Elasticsearch
    │
    ├── Need 20+ filters with deep joins?
    │   └── Consider QueryDSL
    │
    └── Just CRUD (create, read by ID, update, delete)?
        └── Use plain JpaRepository methods
```

**The rule of thumb:** If you're writing an `if (param != null)` check to decide whether to include a WHERE clause, you should probably be using Specification.

---

## Summary

### When to Use Specification

- You have **3 or more optional** filter parameters
- Users choose which filters to apply at **runtime**
- You need **date range** queries with optional bounds
- You're building a **search endpoint** with multiple criteria
- You want to **add new filters** without touching existing query code
- You want **type-safe**, **injection-proof** dynamic queries

### When NOT to Use Specification

- The query is **fixed** and never changes
- You only filter by **one or two required** parameters
- You need **aggregation** (GROUP BY, SUM, COUNT)
- You need **database-specific** SQL features
- You need **full-text search** at scale (use Elasticsearch)
- You're doing **batch updates/deletes**

### The One-Sentence Summary

> **Specification is just a fancy way to say: build WHERE clauses from small, reusable pieces.**

Each piece is a simple function that returns one condition (or nothing, if the parameter is null). You snap them together with `.and()` and `.or()`. The database sees a normal SQL query. Your code stays clean, testable, and extensible.

That's all there is to it. It's not scary. It's LEGO for SQL.
