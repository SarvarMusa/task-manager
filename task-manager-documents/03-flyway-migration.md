# Flyway Database Migration Implementation

## Date
**2026-02-26**

## Problem

### Why `ddl-auto: update` is Dangerous

**Analogy:** Imagine renovating your house by letting a robot decide what to change. The robot can add rooms, but it **cannot** remove walls, rename rooms, or rearrange furniture. And if you set it to `create` mode, it demolishes the entire house and rebuilds from scratch every time you restart.

That is exactly how Hibernate's `ddl-auto: update` works. It compares your Java entity classes with the current database schema and tries to "sync" them. Here is what it **can** and **cannot** do:

| Operation | `ddl-auto: update` | Real-World Need |
|---|---|---|
| Add a new column | Yes | Yes |
| Add a new table | Yes | Yes |
| Rename a column | **NO** (creates a new column, old one stays) | Yes |
| Drop a column | **NO** (old column stays forever) | Yes |
| Change column type | **NO** (or risky partial change) | Yes |
| Transform existing data | **NO** | Yes |
| Add NOT NULL to column with existing data | **CRASHES** | Yes |
| Reorder columns | **NO** | Sometimes |

### Concrete Disaster Scenario

Suppose you have a `tasks` table with 10,000 rows and you add a new field to your entity:

```java
// You add this to Task.java
@Column(name = "assigned_team", nullable = false)  // NOT NULL!
private String assignedTeam;
```

**What happens with `ddl-auto: update`:**

1. Hibernate sees a new `assigned_team` column is needed
2. It generates: `ALTER TABLE tasks ADD COLUMN assigned_team VARCHAR(255) NOT NULL`
3. **BOOM!** The database rejects this because 10,000 existing rows have no value for `assigned_team`
4. Your application **fails to start** in production
5. You are now debugging at 3 AM with angry customers

**What happens with Flyway:**

```sql
-- V5__add_assigned_team_to_tasks.sql
-- Step 1: Add the column as nullable first
ALTER TABLE tasks ADD COLUMN assigned_team VARCHAR(255);

-- Step 2: Fill existing rows with a sensible default
UPDATE tasks SET assigned_team = 'Unassigned' WHERE assigned_team IS NULL;

-- Step 3: NOW make it NOT NULL (safe because every row has a value)
ALTER TABLE tasks ALTER COLUMN assigned_team SET NOT NULL;
```

No crashes. No data loss. Every step is intentional and reviewable.

### Other Real Problems with `ddl-auto: update`

1. **Ghost columns:** You rename `userName` to `username` in Java. Hibernate creates `username` but leaves the old `user_name` column in the database, wasting space and causing confusion.
2. **No history:** There is no record of what changed and when. If something breaks, you have no way to trace which change caused it.
3. **Team chaos:** Developer A adds a field, Developer B removes it. With `update`, the column stays forever. Nobody knows what is current.
4. **Production roulette:** It works on your laptop with 5 test rows. In production with millions of rows, the `ALTER TABLE` might lock the table for minutes.

---

## Solution

### What is Flyway?

Flyway is a **database migration tool** that treats your database schema the way Git treats your source code. Instead of letting Hibernate guess what to change, you write **explicit SQL scripts** that describe each change step by step.

Think of it like this:
- **Without Flyway:** "Hey database, figure out what changed and update yourself" (scary)
- **With Flyway:** "Here are the exact SQL commands to run, in order, with a version number" (safe)

### Step 1: Add the Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <!-- Version is managed by Spring Boot parent (3.2.0) -->
</dependency>
```

Spring Boot auto-configures Flyway when this dependency is on the classpath. No extra `@EnableFlyway` annotation needed.

### Step 2: Configure Flyway

#### Development Profile (`application-dev.yml`)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # Only CHECK if entities match DB, never change DB
  flyway:
    enabled: true            # Turn Flyway on
    baseline-on-migrate: true  # Handle existing databases gracefully
    baseline-version: 0      # Treat everything before V1 as "already done"
    locations: classpath:db/migration  # Where to find SQL files
    validate-on-migrate: true  # Verify file checksums haven't been tampered with
```

#### Production Profile (`application-prod.yml`)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # Same: only validate, never auto-change
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
    validate-on-migrate: true
```

#### Configuration Parameters Explained

| Parameter | Value | What It Does | Why We Use It |
|---|---|---|---|
| `ddl-auto` | `validate` | Hibernate **only checks** that entities match the DB schema. If they do not match, the app fails to start with a clear error. | Prevents accidental schema changes. Flyway handles all changes. |
| `flyway.enabled` | `true` | Activates Flyway on application startup | We want automatic migration |
| `baseline-on-migrate` | `true` | If the `flyway_schema_history` table does not exist yet, Flyway creates it and marks existing migrations as "already applied" | Essential for adding Flyway to an existing database that was previously managed by `ddl-auto` |
| `baseline-version` | `0` | The version number assigned to the baseline. All migrations with version > 0 will be executed. | Since our first migration is V1, setting baseline to 0 means V1 and above will run |
| `locations` | `classpath:db/migration` | The folder where Flyway looks for migration SQL files | Standard convention; maps to `src/main/resources/db/migration/` |
| `validate-on-migrate` | `true` | Before running new migrations, Flyway checks that previously applied migration files have not been modified (via checksum comparison) | Prevents someone from secretly changing a migration that was already run |

### All `ddl-auto` Values Compared

| Value | Behavior | When to Use |
|---|---|---|
| `none` | Hibernate does nothing to the schema | When you manage everything manually (no Flyway either) |
| `validate` | Checks entities match the schema, throws error if not | **Best for production with Flyway** |
| `update` | Tries to add missing columns/tables (never removes) | Quick prototyping only, NEVER production |
| `create` | **Drops all tables**, recreates from entities | Only for throwaway test databases |
| `create-drop` | Like `create`, but also drops tables when app shuts down | Only for unit tests |

### Step 3: Create Migration Files

#### File Location

```
src/main/resources/
  db/
    migration/
      V1__create_initial_schema.sql
      V2__add_due_date_to_tasks.sql
      V3__fix_task_status_enum_values.sql
      V4__fix_all_enum_values.sql
```

#### Naming Convention

```
V{version}__{description}.sql
│ │         │
│ │         └── Human-readable description (underscores for spaces)
│ │
│ └── TWO underscores (not one!) - this is the separator
│
└── "V" prefix (uppercase) means "versioned migration"
```

**Rules:**
- Version numbers must be **unique** and **sequential** (V1, V2, V3...)
- Two underscores `__` separate version from description (common mistake: using only one)
- Description uses underscores for spaces: `add_due_date_to_tasks` not `add due date to tasks`
- Files are **immutable** - once a migration has run, NEVER edit it. Write a new migration instead.
- File extension must be `.sql`

**Valid examples:**
- `V1__create_initial_schema.sql`
- `V2__add_due_date_to_tasks.sql`
- `V10__add_user_roles_table.sql`

**Invalid examples:**
- `V1_create_schema.sql` (only one underscore)
- `v1__create_schema.sql` (lowercase `v`)
- `V1__create schema.sql` (spaces in filename)

### Baseline Migration (V1)

This is the initial migration that captures the existing database schema. If the database already has these tables (from the old `ddl-auto: update` era), `baseline-on-migrate: true` tells Flyway to skip this and mark it as "done."

```sql
-- V1__create_initial_schema.sql
-- This migration represents the existing database schema.
-- If these tables already exist, Flyway will mark this as "applied"
-- thanks to baseline-on-migrate: true.

-- =============================================
-- USERS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- TASKS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    priority VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- =============================================
-- INDEXES (for query performance)
-- =============================================
CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_tasks_is_active ON tasks(is_active);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);
```

**Key detail:** We use `IF NOT EXISTS` everywhere. This makes the migration **idempotent** - it is safe to run even if the tables already exist.

### Example V2 Migration

```sql
-- V2__add_due_date_to_tasks.sql
-- Adding a due_date column to the tasks table.
-- The column is nullable, so existing rows are not affected.

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS due_date TIMESTAMP;

-- Optional: Set a default due_date for existing tasks
-- UPDATE tasks SET due_date = created_at + INTERVAL '7 days' WHERE due_date IS NULL;
```

Then you also update the Java entity:

```java
// In Task.java, add this field:
@Column(name = "due_date")
private LocalDateTime dueDate;
```

Flyway adds the column to the database. The `ddl-auto: validate` setting confirms that the entity and the database match. Everything is in sync.

---

## How Flyway Works

### First Run (New Database)

```
Application starts
        │
        v
Does flyway_schema_history table exist?
        │
        NO
        │
        v
Create flyway_schema_history table
        │
        v
baseline-on-migrate: true
  └── Insert baseline record (version 0)
        │
        v
Scan db/migration/ folder
  └── Found: V1, V2, V3, V4
        │
        v
Execute V1__create_initial_schema.sql
  └── Record in flyway_schema_history: version=1, success=true
        │
        v
Execute V2__add_due_date_to_tasks.sql
  └── Record in flyway_schema_history: version=2, success=true
        │
        v
Execute V3__fix_task_status_enum_values.sql
  └── Record in flyway_schema_history: version=3, success=true
        │
        v
Execute V4__fix_all_enum_values.sql
  └── Record in flyway_schema_history: version=4, success=true
        │
        v
Hibernate validates entities match schema
  └── Match? YES -> Application starts successfully
```

### Subsequent Runs (No New Migrations)

```
Application starts
        │
        v
Does flyway_schema_history table exist?
        │
        YES
        │
        v
Scan db/migration/ folder
  └── Found: V1, V2, V3, V4
        │
        v
Check flyway_schema_history
  └── Already applied: V1, V2, V3, V4
        │
        v
Validate checksums of V1-V4
  └── Match? YES -> Continue
        │
        v
Any new migrations?
  └── NO -> Skip migration phase
        │
        v
Hibernate validates entities match schema
  └── Application starts successfully (no changes made)
```

### Adding a New Migration (V5)

```
Developer creates V5__add_user_avatar.sql
        │
        v
Application starts
        │
        v
flyway_schema_history has: V1, V2, V3, V4
        │
        v
Scan db/migration/ folder
  └── Found: V1, V2, V3, V4, V5 (NEW!)
        │
        v
Validate checksums of V1-V4
  └── Match? YES -> Continue
        │
        v
Execute V5__add_user_avatar.sql
  └── Record in flyway_schema_history: version=5, success=true
        │
        v
Hibernate validates entities match schema
  └── Application starts successfully
```

### Existing Production Database (First Time Adding Flyway)

This is the most common real-world scenario: you already have a database managed by `ddl-auto: update` and you want to switch to Flyway.

```
Application starts with Flyway for the first time
        │
        v
Does flyway_schema_history table exist?
        │
        NO (first time using Flyway)
        │
        v
baseline-on-migrate: true
  └── Create flyway_schema_history table
  └── Insert baseline record: version=0, description="<< Flyway Baseline >>"
        │
        v
Scan db/migration/ folder
  └── Found: V1, V2, V3, V4
        │
        v
V1 (version 1) > baseline (version 0)
  └── Execute V1__create_initial_schema.sql
  └── Tables already exist? IF NOT EXISTS prevents errors!
        │
        v
V2, V3, V4 also execute
  └── IF NOT EXISTS / safe UPDATE WHERE clauses prevent errors
        │
        v
All migrations recorded in flyway_schema_history
        │
        v
Hibernate validates -> Application starts
```

---

## The `flyway_schema_history` Table

Flyway automatically creates and maintains a table called `flyway_schema_history` in your database. This is its "memory" - it tracks every migration that has been applied.

### Example Data

| installed_rank | version | description | type | script | checksum | installed_by | installed_on | execution_time | success |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 0 | << Flyway Baseline >> | BASELINE | << Flyway Baseline >> | NULL | admin | 2026-02-26 10:00:00 | 0 | true |
| 2 | 1 | create initial schema | SQL | V1__create_initial_schema.sql | -1287456321 | admin | 2026-02-26 10:00:01 | 245 | true |
| 3 | 2 | add due date to tasks | SQL | V2__add_due_date_to_tasks.sql | 982374561 | admin | 2026-02-26 10:00:01 | 12 | true |
| 4 | 3 | fix task status enum values | SQL | V3__fix_task_status_enum_values.sql | -456789123 | admin | 2026-02-26 10:00:01 | 8 | true |
| 5 | 4 | fix all enum values | SQL | V4__fix_all_enum_values.sql | 123456789 | admin | 2026-02-26 10:00:02 | 35 | true |

### Column Explanations

| Column | Purpose |
|---|---|
| `installed_rank` | Auto-incrementing order of application |
| `version` | The version number from the filename (V**1**, V**2**, etc.) |
| `description` | Extracted from the filename (underscores become spaces) |
| `type` | `SQL` for SQL migrations, `BASELINE` for the initial baseline |
| `script` | The exact filename that was executed |
| `checksum` | A hash of the file content. If someone modifies a previously run file, the checksum will not match and Flyway will refuse to start |
| `installed_by` | The database user who ran the migration |
| `installed_on` | Timestamp of when the migration ran |
| `execution_time` | How long the migration took (in milliseconds) |
| `success` | `true` if the migration completed without errors |

**Important:** Never manually edit this table unless you are an expert. If you need to "undo" a migration, write a new migration that reverses it.

---

## Common Migration Scenarios

Here are 7 real-world scenarios you will encounter, each with the SQL migration and corresponding Java entity change.

### 1. Add a Nullable Column

The simplest migration. No risk to existing data.

```sql
-- V5__add_notes_to_tasks.sql
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS notes TEXT;
```

```java
// Task.java - add this field
@Column(name = "notes")
private String notes;
// + getter and setter
```

**Why this is safe:** Existing rows get `NULL` for the new column. No data is affected.

### 2. Add a NOT NULL Column with Default Value

You need every row to have a value, but rows already exist.

```sql
-- V6__add_category_to_tasks.sql

-- Step 1: Add as nullable
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS category VARCHAR(50);

-- Step 2: Set a default for all existing rows
UPDATE tasks SET category = 'GENERAL' WHERE category IS NULL;

-- Step 3: Now enforce NOT NULL
ALTER TABLE tasks ALTER COLUMN category SET NOT NULL;

-- Step 4: Set default for future inserts
ALTER TABLE tasks ALTER COLUMN category SET DEFAULT 'GENERAL';
```

```java
// Task.java
@Column(name = "category", nullable = false)
private String category = "GENERAL";  // Java-side default too
```

**Why this is safe:** We fill existing rows BEFORE adding the NOT NULL constraint. No crashes.

### 3. Rename a Column

`ddl-auto: update` **cannot do this at all**. It would create a new column and leave the old one.

```sql
-- V7__rename_description_to_summary.sql
ALTER TABLE tasks RENAME COLUMN description TO summary;
```

```java
// Task.java - change the field
@Column(name = "summary", length = 500)   // was "description"
private String summary;                    // was description
```

**Why Flyway is needed:** This is a single atomic operation. All existing data moves to the new column name. With `ddl-auto`, you would have two columns and lost data.

### 4. Drop a Column

`ddl-auto: update` **never drops columns**, even if you remove the field from your entity.

```sql
-- V8__remove_legacy_field.sql
-- First, make sure no code depends on this column!
ALTER TABLE tasks DROP COLUMN IF EXISTS legacy_field;
```

```java
// Task.java - simply remove the field
// (delete the field, getter, and setter)
```

### 5. Change a Column's Data Type

Dangerous with `ddl-auto`, precise with Flyway.

```sql
-- V9__change_description_type.sql
-- Change description from VARCHAR(500) to TEXT (unlimited length)
ALTER TABLE tasks ALTER COLUMN description TYPE TEXT;
```

```java
// Task.java
@Column(name = "description", columnDefinition = "TEXT")
private String description;
```

**Why Flyway is needed:** You control the exact ALTER statement. You can add `USING` clauses for complex type conversions (e.g., `VARCHAR` to `INTEGER`).

### 6. Data Transformation

This is where Flyway truly shines. `ddl-auto` has absolutely no way to transform data.

This is exactly what our V3 and V4 migrations do - converting old enum values to new ones:

```sql
-- V4__fix_all_enum_values.sql

-- Convert old status values to new enum format
UPDATE tasks SET status = 'PENDING'
WHERE UPPER(TRIM(status)) IN ('TODO', 'NEW', 'OPEN', 'CREATED', 'PENDING')
  AND status != 'PENDING';

UPDATE tasks SET status = 'IN_PROGRESS'
WHERE UPPER(TRIM(status)) IN ('IN_PROGRESS', 'INPROGRESS', 'IN-PROGRESS', 'ACTIVE', 'STARTED', 'DOING', 'WIP')
  AND status != 'IN_PROGRESS';

UPDATE tasks SET status = 'COMPLETED'
WHERE UPPER(TRIM(status)) IN ('COMPLETED', 'DONE', 'FINISHED', 'CLOSED', 'RESOLVED')
  AND status != 'COMPLETED';

UPDATE tasks SET status = 'CANCELLED'
WHERE UPPER(TRIM(status)) IN ('CANCELLED', 'CANCELED', 'CANCEL', 'DELETED', 'REMOVED', 'REJECTED')
  AND status != 'CANCELLED';

-- Catch-all: anything unrecognized becomes PENDING
UPDATE tasks SET status = 'PENDING'
WHERE status NOT IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

-- Same approach for priority values...
UPDATE tasks SET priority = 'LOW'
WHERE UPPER(TRIM(priority)) IN ('LOW', 'LO', 'LOWEST', 'MINOR', 'L')
  AND priority != 'LOW';

-- ... (MEDIUM and HIGH follow the same pattern)
```

**Real-world lesson:** This is incredibly common. Enum values change, typos accumulate, old code inserts different formats. With Flyway, you can clean up all of this in a single, auditable migration.

### 7. Create a New Table with Foreign Key

```sql
-- V10__create_comments_table.sql
CREATE TABLE IF NOT EXISTS comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    task_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_comments_task_id ON comments(task_id);
CREATE INDEX IF NOT EXISTS idx_comments_user_id ON comments(user_id);
```

```java
// Comment.java - new entity
@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

---

## What We Gained

### 1. Data Safety
Every schema change is an explicit, reviewed SQL statement. No automatic guessing. If a migration is wrong, it fails loudly at startup instead of silently corrupting data.

### 2. Schema Versioning
Every version of the database schema is captured in a numbered migration file. You can look at `V1` through `V4` and reconstruct the exact state of the database at any point in history.

### 3. Reproducibility
Any developer can clone the repository, start the application, and get an identical database. No more "it works on my machine" problems because everyone runs the same migrations in the same order.

### 4. Safe Entity Changes
You can now confidently rename fields, drop columns, change types, and transform data. Before, half of these operations were impossible with `ddl-auto`.

### 5. Team Collaboration
When two developers both need to change the schema, they create separate migration files (V5 and V6). There is no conflict - Flyway runs them in order. With `ddl-auto`, both developers would just change entities and hope for the best.

### 6. Rollback Capability
While Flyway Community Edition does not have automatic rollback, you can write "undo" migrations. For example, if V5 adds a column, V6 can remove it. The full audit trail in `flyway_schema_history` tells you exactly what happened.

---

## Advantages

| Advantage | Description |
|---|---|
| **Full control** | You write the exact SQL that runs. No surprises. |
| **Version history** | Every change is a numbered file in source control. |
| **Team-friendly** | Multiple developers can add migrations without conflicts. |
| **Environment consistency** | Dev, staging, and production all have the same schema. |
| **Safe data migration** | Transform, clean, and fix data alongside schema changes. |
| **Validation** | Checksums catch unauthorized modifications to migration files. |
| **Incremental** | Only new migrations run. Previously applied ones are skipped. |
| **Spring Boot integration** | Zero extra configuration beyond adding the dependency. |
| **Database-agnostic** | Works with PostgreSQL, MySQL, H2, Oracle, SQL Server, and more. |
| **Production-proven** | Used by thousands of production systems worldwide. |

## Disadvantages

| Disadvantage | Description |
|---|---|
| **Extra files to manage** | Every schema change requires a new SQL file (but this is also a feature). |
| **Manual SQL writing** | You need to know SQL (but you should know SQL anyway). |
| **Immutable history** | Once a migration runs, you cannot edit it. You must create a new one to fix mistakes. |
| **No automatic rollback** | Community Edition does not support `undo` migrations. You write reverse migrations manually. |
| **Learning curve** | Developers used to `ddl-auto: update` need to learn migration thinking. |
| **Initial setup effort** | Converting from `ddl-auto` to Flyway requires capturing the existing schema in V1. |
| **Merge conflicts possible** | If two developers create the same version number (V5), one must be renumbered. |

---

## Affected Files

| File | Change |
|---|---|
| `pom.xml` | Added `flyway-core` dependency |
| `application-dev.yml` | Changed `ddl-auto` from `update` to `validate`, added Flyway config |
| `application-prod.yml` | Changed `ddl-auto` from `update` to `validate`, added Flyway config |
| `src/main/resources/db/migration/V1__create_initial_schema.sql` | Baseline migration with users & tasks tables |
| `src/main/resources/db/migration/V2__add_due_date_to_tasks.sql` | Added `due_date` column to tasks |
| `src/main/resources/db/migration/V3__fix_task_status_enum_values.sql` | Converted `TODO` status to `PENDING` |
| `src/main/resources/db/migration/V4__fix_all_enum_values.sql` | Standardized all enum values for status & priority |
| `Task.java` (entity) | Added `dueDate` field to match V2 migration |

---

## Before/After Comparison

### OLD Workflow (with `ddl-auto: update`)

```
Developer changes Java entity
        │
        v
Application starts
        │
        v
Hibernate compares entities with database
        │
        v
Hibernate generates ALTER TABLE statements automatically
        │
        v
SQL runs (maybe correctly, maybe not)
        │
        v
NO record of what changed
        │
        v
Other developers pull code
        │
        v
Their Hibernate ALSO tries to sync
        │
        v
Possible conflicts, ghost columns, data loss
        │
        v
Production deployment: "let's hope it works"
```

### NEW Workflow (with Flyway)

```
Developer changes Java entity
        │
        v
Developer writes a migration SQL file (V5__description.sql)
        │
        v
Code review: team reviews BOTH entity change AND SQL migration
        │
        v
Application starts
        │
        v
Flyway checks flyway_schema_history
        │
        v
Flyway runs ONLY new migrations (V5)
        │
        v
Change is recorded in flyway_schema_history
        │
        v
Hibernate validates entities match schema
        │
        v
Other developers pull code, same V5 migration runs
        │
        v
Everyone has identical database schema
        │
        v
Production deployment: same migration runs, 100% predictable
```

---

## Error Messages and Troubleshooting

### 1. Missing Migration File

**Error:**
```
Flyway detected resolved migrations that were not applied:
  V3__fix_task_status_enum_values.sql
```

**Cause:** A migration file that was previously applied is now missing from the project.

**Fix:** Restore the file. Flyway needs all historically-applied migrations to be present for checksum validation.

### 2. Checksum Mismatch

**Error:**
```
Migration checksum mismatch for migration version 2
  -> Applied to database:  982374561
  -> Resolved locally:     -123456789
```

**Cause:** Someone edited a migration file that was already applied to the database.

**Fix:** NEVER edit applied migrations. Either:
- Restore the original file content
- If you must fix it: `flyway repair` (resets checksums in flyway_schema_history), but only do this if you are absolutely sure the database state is correct

### 3. Version Conflict

**Error:**
```
Found more than one migration with version 5
  -> V5__add_notes.sql
  -> V5__add_category.sql
```

**Cause:** Two developers both created a V5 migration.

**Fix:** Rename one to V6. Communicate with your team about version number coordination.

### 4. Validation Error (Entity-Schema Mismatch)

**Error:**
```
Schema-validation: missing column [due_date] in table [tasks]
```

**Cause:** You added a field to the Java entity but forgot to write a Flyway migration for it.

**Fix:** Write the missing migration:
```sql
-- V5__add_missing_due_date.sql
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS due_date TIMESTAMP;
```

### 5. Migration Failed Mid-Way

**Error:**
```
Migration V5__complex_migration.sql failed
  -> SQL: ALTER TABLE tasks ADD COLUMN ...
  -> Error: column "x" already exists
```

**Cause:** The migration partially succeeded before failing. PostgreSQL wraps each migration in a transaction, so it should auto-rollback. But if using MySQL (which does not support transactional DDL), you may have a partial state.

**Fix:** Fix the SQL, and if on a non-transactional database, manually clean up the partial change before re-running.

---

## Migration Best Practices

### 1. Always Use `IF NOT EXISTS` / `IF EXISTS`

```sql
-- GOOD: Safe to run multiple times
CREATE TABLE IF NOT EXISTS comments (...);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE tasks DROP COLUMN IF EXISTS legacy_field;

-- BAD: Will fail if already exists
CREATE TABLE comments (...);        -- Error if table exists
ALTER TABLE tasks ADD COLUMN notes TEXT;  -- Error if column exists
```

### 2. Break Large Updates into Batches

For tables with millions of rows, updating all at once can lock the table:

```sql
-- BAD: Locks the entire table for the duration
UPDATE tasks SET status = 'PENDING' WHERE status = 'TODO';

-- BETTER: Process in batches (PostgreSQL example)
DO $$
DECLARE
    batch_size INT := 10000;
    rows_updated INT;
BEGIN
    LOOP
        UPDATE tasks SET status = 'PENDING'
        WHERE id IN (
            SELECT id FROM tasks
            WHERE status = 'TODO'
            LIMIT batch_size
        );
        GET DIAGNOSTICS rows_updated = ROW_COUNT;
        EXIT WHEN rows_updated = 0;
        RAISE NOTICE 'Updated % rows', rows_updated;
    END LOOP;
END $$;
```

### 3. Keep Migrations Atomic

Each migration should do one logical thing. Do not combine unrelated changes:

```sql
-- BAD: Two unrelated changes in one file
ALTER TABLE tasks ADD COLUMN notes TEXT;
CREATE TABLE audit_log (...);

-- GOOD: Separate files
-- V5__add_notes_to_tasks.sql
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS notes TEXT;

-- V6__create_audit_log_table.sql
CREATE TABLE IF NOT EXISTS audit_log (...);
```

### 4. Plan a Rollback Strategy

For every migration, think about how to reverse it:

```sql
-- V5__add_category_to_tasks.sql (forward)
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS category VARCHAR(50) DEFAULT 'GENERAL';

-- V6__rollback_category_from_tasks.sql (reverse, if needed)
ALTER TABLE tasks DROP COLUMN IF EXISTS category;
```

### 5. Test Migrations on a Copy of Production Data

Never run a migration on production for the first time. Always test against a copy of the production database to catch data-specific issues.

### 6. Add Comments to Complex Migrations

```sql
-- V4__fix_all_enum_values.sql
-- PURPOSE: Standardize all status and priority values to match Java enums
-- CONTEXT: Old code allowed freeform strings. New code uses strict enums.
-- VALID VALUES:
--   TaskStatus:   PENDING, IN_PROGRESS, COMPLETED, CANCELLED
--   TaskPriority: LOW, MEDIUM, HIGH
-- ROLLBACK: Not applicable (data cleanup is not reversible)
```

---

## Performance Comparison

| Operation | `ddl-auto: update` | Flyway |
|---|---|---|
| **Startup time (first run)** | Slower (introspects entire schema) | Fast (runs SQL files sequentially) |
| **Startup time (no changes)** | Moderate (still compares entities vs schema) | Very fast (checks history table only) |
| **Schema comparison** | Every startup (compares all entities) | Only new migrations + checksum validation |
| **Large data migration** | Impossible | Full SQL control (batching, indexes, etc.) |
| **Table locking risk** | Uncontrolled ALTER statements | You control statement order and batching |
| **Memory usage** | Higher (loads full schema metadata) | Minimal (reads SQL files) |
| **Network calls** | Multiple (introspect + alter) | One per migration file |

---

## Future Improvements

1. **Repeatable Migrations:** Add `R__` prefixed files for views, stored procedures, or seed data that need to be re-applied when changed.
2. **Flyway Callbacks:** Use `beforeMigrate.sql` and `afterMigrate.sql` for pre/post migration actions (e.g., disable triggers during migration).
3. **Environment-Specific Migrations:** Use `locations` to have separate migration folders for different databases (H2 vs PostgreSQL).
4. **Flyway Maven Plugin:** Add `flyway-maven-plugin` for command-line migration management (`mvn flyway:info`, `mvn flyway:migrate`, `mvn flyway:repair`).
5. **Dry Run Mode:** Use `flyway info` to preview pending migrations without executing them.
6. **Migration Testing:** Add integration tests that verify migrations run successfully against a clean database.
7. **CI/CD Integration:** Run `flyway validate` in CI pipeline to catch migration issues before deployment.

---

## Decision Log

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| **Migration tool** | Flyway | Liquibase | Flyway uses plain SQL (lower learning curve). Liquibase uses XML/YAML/JSON which adds complexity. For a project that uses PostgreSQL-specific features, plain SQL gives full control. |
| **`ddl-auto` value** | `validate` | `none` | `validate` catches entity-schema mismatches at startup, giving immediate feedback. `none` would silently allow mismatches, potentially causing runtime errors. |
| **`baseline-version`** | `0` | `1` | Setting to `0` ensures that V1 migration (the baseline schema) is executed for new databases. Setting to `1` would skip V1. |
| **`baseline-on-migrate`** | `true` | `false` | `true` handles the transition from `ddl-auto` to Flyway gracefully. Without it, Flyway would fail on existing databases that lack `flyway_schema_history`. |
| **File location** | `classpath:db/migration` | Custom path | This is the Flyway default convention. Following conventions makes the project easier for new team members to understand. |
| **`validate-on-migrate`** | `true` | `false` | Ensures no one has tampered with applied migration files. Adds minimal overhead (just checksum comparison). |
| **SQL vs Java migrations** | SQL | Java-based migrations | SQL is simpler, more readable, and works directly with the database. Java-based migrations are only needed for complex logic (e.g., calling external APIs during migration). |

---

## Conclusion

Switching from `ddl-auto: update` to Flyway is one of the most impactful improvements you can make to a Spring Boot project's database management. Here is the core takeaway:

**`ddl-auto: update` can ADD columns but CANNOT rename, drop, or transform data. Flyway does ALL of these safely.**

With Flyway:
- Every schema change is a **versioned, reviewable SQL file**
- Every environment (dev, staging, production) runs the **exact same migrations**
- Your database has a **complete history** of every change ever made
- You can **safely rename columns, drop tables, transform data**, and do anything SQL supports
- The `flyway_schema_history` table serves as an **audit trail** that tells you exactly what happened, when, and by whom

The initial effort of writing SQL migration files pays for itself the first time you need to rename a column, fix enum values, or deploy to a new environment. From this point forward, the database is no longer a black box - it is a versioned, predictable, and reliable part of your application.
