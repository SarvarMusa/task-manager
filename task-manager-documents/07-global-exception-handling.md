# Global Exception Handling - Complete Guide

## Date: 2026-02-27

---

## Problem

### Why Did We Need This?

**Imagine a hospital where every doctor writes medical reports in a different format** - some use paper, some use email, some just tell the patient verbally. No consistency, no tracking, chaos. When a patient moves between departments, nobody can read the previous doctor's notes. Now replace "hospital" with "API" and "doctors" with "exception handlers" - that was our old approach.

Here are the **6 specific problems** with the old approach:

### Problem 1: DRY Violation (Don't Repeat Yourself)

Every exception handler was building error responses manually with `HashMap`. The same boilerplate code was copy-pasted across 4+ handlers:

```java
// OLD APPROACH - this same block was repeated in EVERY handler
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
    Map<String, Object> body = new HashMap<>();        // repeated
    body.put("timestamp", LocalDateTime.now());         // repeated
    body.put("status", 404);                            // repeated
    body.put("message", ex.getMessage());               // repeated
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}

@ExceptionHandler(UserAlreadyExistsException.class)
public ResponseEntity<Map<String, Object>> handleConflict(UserAlreadyExistsException ex) {
    Map<String, Object> body = new HashMap<>();        // same thing again!
    body.put("timestamp", LocalDateTime.now());         // same thing again!
    body.put("status", 409);                            // same thing again!
    body.put("message", ex.getMessage());               // same thing again!
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
}
```

**Why this is bad:** If you need to add a new field to all error responses (like `requestId`), you have to find and update **every single handler**. Miss one? Inconsistent responses.

### Problem 2: No Programmatic Error Codes

The client (frontend, mobile app) had no machine-readable way to distinguish between errors:

```json
// OLD: Both return 400, but for completely different reasons
{"status": 400, "message": "Invalid email format"}
{"status": 400, "message": "Username is too short"}
```

**The real-world pain:** A frontend developer wants to show a red border on the email field specifically. With just a message string, they have to do fragile string matching:

```javascript
// TERRIBLE frontend code forced by our old API
if (error.message.includes("email")) {
    highlightEmailField(); // What if the message changes? Breaks silently.
}
```

### Problem 3: Validation Errors Not Detailed

When a user submitted a form with 3 invalid fields, the old approach only showed the first error:

```json
// OLD: Which field failed? The user has to guess.
{"status": 400, "message": "Validation failed"}
```

**The real-world pain:** User fixes one field, resubmits, gets ANOTHER error, fixes it, resubmits, gets ANOTHER error... This "whack-a-mole" experience is terrible UX.

### Problem 4: No Request Path Information

When multiple endpoints returned the same error, you couldn't tell which one broke:

```json
// OLD: Was this from /api/tasks, /api/users, or /api/projects?
{"status": 404, "message": "Resource not found"}
```

**The real-world pain:** In production at 2 AM, you're looking at an error log. You see "Resource not found" but you have 20 endpoints. Good luck finding which one caused it.

### Problem 5: Missing Exception Types

The old handler only caught 3-4 exceptions. All others fell through to Spring's default handler, producing inconsistent responses:

```json
// What happened when someone sent DELETE to a GET-only endpoint?
// Spring's default (ugly, inconsistent with our format):
{
    "timestamp": "2026-02-27T10:30:00.000+00:00",
    "status": 405,
    "error": "Method Not Allowed",
    "path": "/api/v1/tasks"
}

// What about a malformed UUID? An unsupported content type?
// Each one produced a DIFFERENT format. Total chaos.
```

### Problem 6: Security Risk - Stack Trace Leaking

The worst problem. Unhandled exceptions exposed internal server details:

```json
// OLD: This was being sent to users in production!
{
    "status": 500,
    "message": "could not execute statement; SQL [n/a]; constraint [uk_users_email]; ...",
    "trace": "org.hibernate.exception.ConstraintViolationException\n\tat org.hibernate.internal.ExceptionConverterImpl...\n\tat com.zaxxer.hikari.pool.ProxyPreparedStatement..."
}
```

**Why this is dangerous:**
- Attackers now know you use Hibernate, HikariCP, and PostgreSQL
- They can see your database constraint names (table structure)
- They can see your package structure and class names
- This is like handing a burglar your house blueprint

---

## Solution

We built three components that work together like a well-oiled machine:

### Component 1: ErrorCode Enum - The Error Dictionary

**Think of error codes like ZIP codes for errors.** Instead of saying "somewhere in New York", you say "10001" and everyone knows exactly where. Instead of "something went wrong", you say `RESOURCE_NOT_FOUND` and every system knows exactly what happened.

```java
public enum ErrorCode {

    // 4xx Client Errors - "The client did something wrong"
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, 
            "The requested resource could not be found"),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", HttpStatus.CONFLICT, 
            "A user with this identifier already exists"),
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, 
            "The request contains invalid or missing fields"),
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, 
            "The request could not be processed"),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, 
            "Authentication is required"),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, 
            "You do not have permission to access this resource"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, 
            "The HTTP method is not supported for this endpoint"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
            "The content type is not supported"),
    CONSTRAINT_VIOLATION("CONSTRAINT_VIOLATION", HttpStatus.BAD_REQUEST, 
            "A constraint violation occurred"),

    // 5xx Server Errors - "The server did something wrong"
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, 
            "An unexpected error occurred"),
    DATABASE_ERROR("DATABASE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, 
            "A database error occurred"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, 
            "The service is temporarily unavailable");

    private final String code;          // Machine-readable identifier
    private final HttpStatus httpStatus; // The HTTP status to return
    private final String defaultMessage; // Human-readable description

    // ... constructor and getters
}
```

**Why an enum and not just strings?**
- **Compile-time safety:** Typo in `"RESORCE_NOT_FOUND"` compiles fine but fails silently. `ErrorCode.RESORCE_NOT_FOUND` won't compile at all.
- **Single source of truth:** Change the message in one place, it updates everywhere.
- **IDE support:** Autocomplete, refactoring, find-usages all work.

### Component 2: ErrorResponse DTO - The Standard Envelope

Every error response from our API now uses this exact structure. Think of it as a standardized medical report form - every doctor fills the same form, every nurse can read it:

```java
public class ErrorResponse {
    private LocalDateTime timestamp;      // When the error happened
    private int status;                   // HTTP status code (404, 400, 500...)
    private String errorCode;             // Machine-readable code ("RESOURCE_NOT_FOUND")
    private String message;               // Human-readable explanation
    private Map<String, String> errors;   // Field-level details (for validation)
    private String path;                  // Which endpoint was called
    private String error;                 // General error category

    // Builder pattern for clean construction
    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }
}
```

**Why a DTO instead of a `Map<String, Object>`?**

| Aspect | `Map<String, Object>` | `ErrorResponse` DTO |
|--------|----------------------|---------------------|
| Type safety | None - `body.put("stauts", 404)` compiles fine (typo!) | Full - `response.setStatus(404)` is checked |
| Swagger docs | Cannot auto-generate schema | Auto-generates a complete schema |
| Consistency | Each dev might use different keys | Everyone uses the same fields |
| Null safety | `get()` returns `Object`, needs casting | Getters return proper types |
| IDE support | No autocomplete for keys | Full autocomplete |

**Before vs After:**

```java
// BEFORE: Fragile, error-prone, no autocomplete
Map<String, Object> body = new HashMap<>();
body.put("timestamp", LocalDateTime.now());
body.put("status", 404);
body.put("mesage", ex.getMessage());  // typo - nobody catches this!

// AFTER: Clean, safe, readable
ErrorResponse response = ErrorResponse.builder()
    .status(ErrorCode.RESOURCE_NOT_FOUND.getStatusCode())     // 404
    .errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())        // "RESOURCE_NOT_FOUND"
    .message(ex.getMessage())                                   // specific message
    .path(request.getRequestURI())                             // "/api/v1/tasks/123"
    .error(ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage())   // category description
    .build();
```

### Component 3: Refactored GlobalExceptionHandler

The handler uses `@RestControllerAdvice` with a centralized `buildErrorResponse()` helper to eliminate all duplication:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_LOG_TEMPLATE = "Error occurred - Code: {}, Path: {}, Message: {}";

    // 15 exception handlers, each following the same pattern:
    // 1. Log the error (with appropriate level)
    // 2. Build a standardized ErrorResponse
    // 3. Return with the correct HTTP status

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        // log.warn - not log.error, because a 404 is the CLIENT's fault, not ours
        log.warn(ERROR_LOG_TEMPLATE, ErrorCode.RESOURCE_NOT_FOUND.getCode(), 
                request.getRequestURI(), ex.getMessage());
        
        ErrorResponse response = buildErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ... 14 more handlers ...

    // THE KEY: One helper method used by all handlers
    private ErrorResponse buildErrorResponse(ErrorCode errorCode, String message, String path) {
        return ErrorResponse.builder()
                .status(errorCode.getStatusCode())
                .errorCode(errorCode.getCode())
                .message(message)
                .path(path)
                .error(errorCode.getDefaultMessage())
                .build();
    }
}
```

---

## What We Gained

### 1. DRY - One Helper Method Instead of 4+ Repeated Blocks

**Before:** Each handler built its own `HashMap` manually (5 lines x 4 handlers = 20 lines of duplication).

**After:** One `buildErrorResponse()` method called everywhere (5 lines total, once).

```java
// Before: repeated in every handler
Map<String, Object> body = new HashMap<>();
body.put("timestamp", LocalDateTime.now());
body.put("status", statusCode);
body.put("message", message);
return ResponseEntity.status(status).body(body);

// After: one-liner in every handler
ErrorResponse response = buildErrorResponse(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request.getRequestURI());
return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
```

### 2. Type Safety - ErrorResponse Instead of Map

**Before:** `Map<String, Object>` - typos in keys, wrong value types, no IDE help.

**After:** `ErrorResponse` - compile-time checks, auto-generated Swagger schema, full IDE support.

```java
// Before: this compiles but produces wrong JSON key
body.put("mesage", ex.getMessage()); // typo! "mesage" instead of "message"

// After: this won't even compile with a typo
response.setMesssage(ex.getMessage()); // COMPILE ERROR - method doesn't exist
```

### 3. Programmatic Error Codes - Machine-Readable Errors

**Before:** Clients parsed message strings (fragile, breaks when message changes).

**After:** Clients use `errorCode` field to make decisions programmatically.

```javascript
// Frontend JavaScript - clean, reliable error handling
async function createTask(taskData) {
    try {
        const response = await fetch('/api/v1/tasks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(taskData)
        });

        if (!response.ok) {
            const error = await response.json();
            
            switch (error.errorCode) {
                case 'VALIDATION_ERROR':
                    // Show field-level errors from error.errors map
                    Object.entries(error.errors).forEach(([field, message]) => {
                        showFieldError(field, message);
                    });
                    break;
                case 'RESOURCE_NOT_FOUND':
                    showNotification('The item was not found. It may have been deleted.');
                    break;
                case 'USER_ALREADY_EXISTS':
                    showNotification('This email is already registered.');
                    break;
                case 'FORBIDDEN':
                    redirectToLogin();
                    break;
                default:
                    showNotification('Something went wrong. Please try again.');
            }
        }
    } catch (networkError) {
        showNotification('Network error. Check your connection.');
    }
}
```

### 4. Detailed Validation - Field-Level Errors

**Before:** "Validation failed" (which field? what's wrong with it?).

**After:** Exact field names with specific error messages.

```json
{
    "errorCode": "VALIDATION_ERROR",
    "message": "Validation failed. Please check your input.",
    "errors": {
        "title": "Title must be between 3 and 100 characters",
        "priority": "Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL",
        "dueDate": "Due date must be in the future"
    }
}
```

**How the handler collects field-level errors:**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationExceptions(
        MethodArgumentNotValidException ex, HttpServletRequest request) {
    
    // Collect ALL field errors into a map, not just the first one
    Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
            .filter(error -> error instanceof FieldError)
            .map(error -> (FieldError) error)
            .collect(Collectors.toMap(
                    FieldError::getField,                      // key = field name
                    error -> Objects.requireNonNullElse(
                            error.getDefaultMessage(), 
                            "Invalid value"),                  // value = error message
                    (existing, replacement) -> 
                            existing + ", " + replacement      // merge if same field has multiple errors
            ));

    ErrorResponse response = ErrorResponse.builder()
            .status(ErrorCode.VALIDATION_ERROR.getStatusCode())
            .errorCode(ErrorCode.VALIDATION_ERROR.getCode())
            .message("Validation failed. Please check your input.")
            .path(request.getRequestURI())
            .error(ErrorCode.VALIDATION_ERROR.getDefaultMessage())
            .errors(errors)  // <-- field-level details included
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
}
```

### 5. Debug with Path Info

**Before:** "Resource not found" (from which endpoint? which request?).

**After:** `"path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000"` - immediately know what was requested.

```json
{
    "status": 404,
    "errorCode": "RESOURCE_NOT_FOUND",
    "message": "Task not found with id: 550e8400-e29b-41d4-a716-446655440000",
    "path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000"
}
```

### 6. Security - No Stack Trace in Production

**Before:** Full stack traces exposed to users, revealing internal architecture.

**After:** Generic message for unexpected errors; details logged server-side only.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex, HttpServletRequest request) {
    // Full details logged internally - for debugging
    log.error("Unexpected error - Path: {}, Error: {}", 
            request.getRequestURI(), ex.getMessage(), ex);

    // Generic message sent to client - no internal details
    String message = "An unexpected error occurred. Please try again later.";

    ErrorResponse response = buildErrorResponse(
            ErrorCode.INTERNAL_SERVER_ERROR, message, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
}
```

**What the user sees:** `"An unexpected error occurred. Please try again later."`  
**What the log file contains:** Full stack trace with class names, line numbers, and root cause.

### 7. 15 Exception Types Handled - Complete Coverage

| # | Exception | HTTP Status | ErrorCode | When It Happens |
|---|-----------|-------------|-----------|-----------------|
| 1 | `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` | Task/User not found by ID |
| 2 | `UserAlreadyExistsException` | 409 | `USER_ALREADY_EXISTS` | Registering with existing email |
| 3 | `InvalidRequestException` | 400 | `BAD_REQUEST` | Invalid request parameters |
| 4 | `AccessDeniedException` | 403 | `FORBIDDEN` | No permission for this action |
| 5 | `BusinessRuleViolationException` | 422 | Dynamic (`ruleCode`) | Business rule broken (e.g., assign to inactive user) |
| 6 | `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | `@Valid` annotation failures |
| 7 | `ConstraintViolationException` | 400 | `CONSTRAINT_VIOLATION` | Jakarta Bean Validation failures |
| 8 | `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | DELETE on a GET-only endpoint |
| 9 | `NoHandlerFoundException` | 404 | `RESOURCE_NOT_FOUND` | Endpoint doesn't exist at all |
| 10 | `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | Sending XML when JSON expected |
| 11 | `HttpMessageNotReadableException` | 400 | `BAD_REQUEST` | Malformed JSON body or invalid UUID |
| 12 | `MissingServletRequestParameterException` | 400 | `BAD_REQUEST` | Required query param missing |
| 13 | `MethodArgumentTypeMismatchException` | 400 | `BAD_REQUEST` | String where number expected |
| 14 | `DataIntegrityViolationException` | 500 | `DATABASE_ERROR` | Unique constraint, FK violation |
| 15 | `Exception` (catch-all) | 500 | `INTERNAL_SERVER_ERROR` | Anything else unexpected |

---

## Advantages

| Advantage | Description | Impact |
|-----------|-------------|--------|
| **Consistency** | Every error response has the same structure, always | Frontend team writes one error handler, it works for all APIs |
| **DRY Code** | One helper method instead of duplicated HashMap blocks | Adding a new field takes 1 change, not 15 |
| **Type Safety** | ErrorResponse DTO catches bugs at compile time | Impossible to have typos in JSON keys |
| **Programmatic Codes** | ErrorCode enum enables machine-readable error handling | Frontend uses `switch` on codes, not string matching |
| **Field-Level Validation** | Every invalid field reported individually | Users fix all errors in one submission |
| **Debug-Friendly** | Path, timestamp, and errorCode in every response | Find and fix production issues in minutes, not hours |
| **Security** | No stack traces, no internal details exposed | Attackers learn nothing about your internal architecture |
| **Swagger Integration** | ErrorResponse auto-generates API docs | API consumers know exactly what errors to expect |
| **Extensibility** | Adding new exception handler takes ~10 lines | New team members can add handlers following the pattern |
| **Logging** | Every error is logged with appropriate level (warn/error) | 4xx = warn (client's fault), 5xx = error (our fault) |

## Disadvantages

| Disadvantage | Description | Mitigation |
|--------------|-------------|------------|
| **Upfront Complexity** | More files and classes than a simple HashMap approach | Pays off immediately after 3+ exception types |
| **Enum Maintenance** | New error types require adding to ErrorCode enum | This is a feature, not a bug - forces discussion |
| **Learning Curve** | New team members must understand the Builder pattern | Well-documented, follows the existing pattern |
| **Potential Over-Engineering** | For a 2-endpoint app, this is overkill | Our app has 15+ endpoints and growing |
| **Tight Coupling to ErrorCode** | All handlers depend on the ErrorCode enum | This is acceptable - it's our domain vocabulary |

---

## Affected Files

| File | Change | Purpose |
|------|--------|---------|
| `dto/response/ErrorResponse.java` | **New** | Standard error response DTO with Builder |
| `exception/ErrorCode.java` | **New** | Enum of all error codes with HTTP status mappings |
| `exception/GlobalExceptionHandler.java` | **Refactored** | Expanded from ~4 handlers to 15, uses Builder pattern |
| `exception/BusinessRuleViolationException.java` | **New** | Custom exception for business rule violations (422) |
| `exception/InvalidRequestException.java` | **New** | Custom exception for invalid requests with field info |
| `exception/AccessDeniedException.java` | **Existing** | Now properly handled by GlobalExceptionHandler |
| `exception/ResourceNotFoundException.java` | **Existing** | Now uses ErrorCode.RESOURCE_NOT_FOUND |
| `exception/UserAlreadyExistsException.java` | **Existing** | Now uses ErrorCode.USER_ALREADY_EXISTS |

---

## API Usage - Error Response Examples

### 1. Resource Not Found (404)

**Request:** `GET /api/v1/tasks/550e8400-e29b-41d4-a716-446655440000`

**Response:**
```json
{
    "timestamp": "2026-02-27T10:30:00",
    "status": 404,
    "errorCode": "RESOURCE_NOT_FOUND",
    "message": "Task not found with id: 550e8400-e29b-41d4-a716-446655440000",
    "errors": {},
    "path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000",
    "error": "The requested resource could not be found"
}
```

**When this happens:** The UUID is valid but no task exists with that ID. Maybe it was deleted, or the user copied the wrong link.

### 2. Validation Error with Field-Level Details (400)

**Request:** `POST /api/v1/tasks`
```json
{
    "title": "",
    "priority": "SUPER_HIGH",
    "dueDate": "2020-01-01"
}
```

**Response:**
```json
{
    "timestamp": "2026-02-27T10:30:00",
    "status": 400,
    "errorCode": "VALIDATION_ERROR",
    "message": "Validation failed. Please check your input.",
    "errors": {
        "title": "Title must be between 3 and 100 characters",
        "priority": "Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL",
        "dueDate": "Due date must be in the future"
    },
    "path": "/api/v1/tasks",
    "error": "The request contains invalid or missing fields"
}
```

**When this happens:** The `@Valid` annotation on the controller parameter triggers Bean Validation, and 3 fields fail. All 3 errors are returned at once so the user can fix everything in one go.

### 3. UUID Format Error (400)

**Request:** `GET /api/v1/tasks/not-a-valid-uuid`

**Response:**
```json
{
    "timestamp": "2026-02-27T10:30:00",
    "status": 400,
    "errorCode": "BAD_REQUEST",
    "message": "Parameter 'id' has invalid value 'not-a-valid-uuid'. Expected type: UUID",
    "errors": {
        "id": "Parameter 'id' has invalid value 'not-a-valid-uuid'. Expected type: UUID"
    },
    "path": "/api/v1/tasks/not-a-valid-uuid",
    "error": "The request could not be processed"
}
```

**When this happens:** The URL contains `not-a-valid-uuid` where a UUID is expected. Spring can't convert the string to a `UUID` object, so `MethodArgumentTypeMismatchException` is thrown.

### 4. Conflict - Duplicate Resource (409)

**Request:** `POST /api/v1/users/register`
```json
{
    "email": "john@example.com",
    "username": "john_doe",
    "password": "SecurePass123!"
}
```

**Response:**
```json
{
    "timestamp": "2026-02-27T10:30:00",
    "status": 409,
    "errorCode": "USER_ALREADY_EXISTS",
    "message": "User already exists with email: john@example.com",
    "errors": {},
    "path": "/api/v1/users/register",
    "error": "A user with this identifier already exists"
}
```

**When this happens:** A user with `john@example.com` already exists in the database. The service layer detects this and throws `UserAlreadyExistsException` before any database constraint is hit.

### 5. Business Rule Violation (422)

**Request:** `PATCH /api/v1/tasks/550e8400-e29b-41d4-a716-446655440000/assign`
```json
{
    "assigneeId": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Response:**
```json
{
    "timestamp": "2026-02-27T10:30:00",
    "status": 422,
    "errorCode": "TASK_ASSIGNMENT_LIMIT",
    "message": "User already has 10 active tasks. Maximum allowed is 10.",
    "errors": {},
    "path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000/assign",
    "error": "Business rule violation"
}
```

**When this happens:** The request is syntactically valid (all fields present, correct types), but a **business rule** prevents the operation. HTTP 422 means "I understood your request, but I can't process it because it violates a business rule."

**Why 422 instead of 400?**
- **400 Bad Request** = "Your request is malformed" (missing fields, wrong types)
- **422 Unprocessable Entity** = "Your request is well-formed but semantically wrong" (business rules)

---

## Decision Log

| Decision | Alternatives Considered | Why We Chose This |
|----------|------------------------|-------------------|
| **ErrorCode enum** | String constants, Integer codes, separate config file | Enum gives compile-time safety, IDE autocomplete, and bundles the HTTP status with the code. String constants allow typos. Integer codes are meaningless without documentation. |
| **ErrorResponse DTO** | `Map<String, Object>`, JSON string, Spring's `ProblemDetail` | DTO gives type safety, auto-generates Swagger schema, prevents key typos. `ProblemDetail` (RFC 7807) was considered but adds Spring coupling and less control over the exact format. |
| **Handle ALL Spring exceptions** | Only handle custom exceptions | Unhandled Spring exceptions produce inconsistent responses and can leak internal details. The catch-all `Exception` handler ensures nothing escapes. |
| **No stack trace in responses** | Include in dev, exclude in prod | Even in dev, the stack trace is in the logs. Keeping it out of responses means the API contract is identical across environments - frontend code works the same everywhere. |
| **Builder pattern for ErrorResponse** | Constructor with many parameters, Lombok `@Builder` | Manual builder avoids Lombok dependency while keeping construction readable. A 7-parameter constructor would be error-prone (which String is which?). |
| **log.warn for 4xx, log.error for 5xx** | log.error for everything | 4xx errors are the client's fault - they don't need to wake up the on-call engineer. 5xx errors are our fault - they do. This distinction matters for alert configuration. |

---

## Future Improvements

| Improvement | Description | Priority |
|-------------|-------------|----------|
| **i18n (Internationalization)** | Error messages in multiple languages based on `Accept-Language` header. Store messages in `messages.properties` files. | Medium |
| **Sentry/ELK Integration** | Send errors to Sentry or ELK Stack for centralized error monitoring with dashboards and alerts. | High |
| **Correlation ID** | Add a unique request ID to every error response. When a user reports a bug, they share this ID and you can trace the entire request flow. (Already implemented in `LoggingAspect`.) | High |
| **Retry-After Header** | For `503 Service Unavailable` and `429 Too Many Requests`, include a `Retry-After` header telling the client when to retry. | Low |
| **RFC 7807 (Problem Details)** | Migrate to the `application/problem+json` standard for even better interoperability with other systems. | Low |
| **Error Rate Monitoring** | Track error rates per ErrorCode to detect spikes (e.g., sudden increase in `VALIDATION_ERROR` might indicate a frontend bug). | Medium |

---

## Test Scenarios

### Unit Tests for GlobalExceptionHandler

```java
@WebMvcTest
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenResourceNotFound_thenReturns404WithErrorCode() throws Exception {
        // Given: A request for a non-existent task
        // When: GET /api/v1/tasks/{non-existent-id}
        // Then: 404 with errorCode = "RESOURCE_NOT_FOUND"
        mockMvc.perform(get("/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void whenValidationFails_thenReturns400WithFieldErrors() throws Exception {
        // Given: An invalid task creation request
        String invalidTask = """
            {"title": "", "priority": "INVALID"}
            """;
        
        // When: POST with invalid data
        // Then: 400 with field-level errors
        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidTask))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.priority").exists());
    }

    @Test
    void whenUUIDFormatInvalid_thenReturns400() throws Exception {
        // Given: A malformed UUID in the path
        mockMvc.perform(get("/api/v1/tasks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    void whenDuplicateUser_thenReturns409() throws Exception {
        // Given: An email that already exists
        String duplicateUser = """
            {"email": "existing@example.com", "username": "test", "password": "Pass123!"}
            """;
        
        // When: POST to register with duplicate email
        // Then: 409 with errorCode = "USER_ALREADY_EXISTS"
        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateUser))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void whenUnexpectedError_thenReturns500WithoutStackTrace() throws Exception {
        // Then: 500 with generic message (no internal details)
        mockMvc.perform(get("/api/v1/some-broken-endpoint"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "An unexpected error occurred. Please try again later."))
                // CRITICAL: No stack trace or internal details
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void whenBusinessRuleViolated_thenReturns422WithRuleCode() throws Exception {
        // Then: 422 with the specific rule code
        mockMvc.perform(patch("/api/v1/tasks/{id}/assign", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignRequest))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("TASK_ASSIGNMENT_LIMIT"))
                .andExpect(jsonPath("$.status").value(422));
    }
}
```

### What to Verify in Each Test

| Test Case | Verify Status | Verify errorCode | Verify errors Map | Verify No Stack Trace |
|-----------|--------------|------------------|-------------------|-----------------------|
| Resource Not Found | 404 | `RESOURCE_NOT_FOUND` | Empty | Yes |
| Validation Error | 400 | `VALIDATION_ERROR` | Has field names | Yes |
| UUID Format | 400 | `BAD_REQUEST` | Has parameter name | Yes |
| Duplicate User | 409 | `USER_ALREADY_EXISTS` | Empty | Yes |
| Business Rule | 422 | Dynamic `ruleCode` | Empty | Yes |
| Unexpected Error | 500 | `INTERNAL_SERVER_ERROR` | Empty | **Critical** |
| Wrong HTTP Method | 405 | `METHOD_NOT_ALLOWED` | Empty | Yes |
| Missing Parameter | 400 | `BAD_REQUEST` | Has param name | Yes |

---

## Conclusion

The old approach was like every doctor in a hospital writing reports in their own format. The new approach is a standardized medical records system:

1. **ErrorCode enum** = the dictionary of possible diagnoses (12 error types, each with an HTTP status and description)
2. **ErrorResponse DTO** = the standardized report form (same fields every time)
3. **GlobalExceptionHandler** = the central records office (15 handlers, one helper method, proper logging)

**The result:** Every error from our API looks the same, contains the right information, is safe to show to users, and is easy for both humans and machines to understand. Frontend developers write one error handler. Backend developers add new exception handlers in 10 lines. DevOps engineers configure alerts based on error codes. Everyone wins.
