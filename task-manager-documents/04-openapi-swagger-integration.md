# OpenAPI/Swagger Integration

## Date
**2026-02-27**

## Problem

### No Interactive API Documentation

**Analogy:** Imagine a restaurant with no menu. Customers walk in and have to go to the kitchen, look at the ingredients, and guess what dishes are available. They might try to order something that does not exist, or ask for "chicken" when the chef calls it "poultry." Every new customer has to figure it out from scratch.

That is exactly what it is like to use an API with no documentation. Instead of reading 500 lines of controller code, you should be able to open a browser and see every endpoint, every parameter, every response - and **test them right there**.

### Real Pain Points Before This Change

**1. Frontend developers guessing request formats:**
```
Frontend dev: "What does the create task endpoint expect?"
Backend dev:  "Check CreateTaskRequest.java, line 14"
Frontend dev: "Is userId a String or UUID? Is it 'user_id' or 'userId'?"
Backend dev:  "It's a String in the DTO but UUID in the entity... just send a UUID as String"
Frontend dev: "What are the valid status values?"
Backend dev:  "Look at TaskStatus.java... PENDING, IN_PROGRESS, COMPLETED, CANCELLED"
Frontend dev: "The old docs said TODO was valid..."
Backend dev:  "We changed that in V3 migration but nobody updated the docs"
```

This conversation happens 10 times a day. Every day.

**2. Manual Postman collection setup:**
Every developer had to manually create Postman requests:
- Type the URL by hand
- Guess the request body format
- Remember which headers are needed
- When endpoints change, manually update every request
- New team members spend hours building their Postman collection

**3. Outdated markdown documentation:**
```markdown
## Create Task                            <-- This was written 3 months ago
POST /api/tasks                           <-- URL changed to /tasks
Body: { "name": "...", "status": "TODO" } <-- Field is now "title", status "TODO" no longer exists
```

Markdown docs become lies within weeks because no one remembers to update them when the code changes.

### The Root Cause

API documentation was **separate** from the code. When code changes, documentation does not update itself. The solution: **generate documentation directly from the code** so it is always current.

---

## Solution

### What We Added

We integrated **SpringDoc OpenAPI** into the project. SpringDoc automatically scans your Spring Boot controllers, reads their annotations, and generates a complete interactive API documentation page (Swagger UI) and a machine-readable OpenAPI 3.0 specification.

### Step 1: Add the Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

That is it. One dependency. Spring Boot auto-configures everything. You immediately get:
- **Swagger UI** at `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON** at `http://localhost:8080/v3/api-docs`
- **OpenAPI YAML** at `http://localhost:8080/v3/api-docs.yaml`

### Step 2: Create OpenAPI Configuration

This is optional but highly recommended. It customizes the API metadata shown in Swagger UI.

```java
// src/main/java/org/task/taskmaganer/config/OpenApiConfig.java

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskManagerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Manager API")                    // Name shown at the top
                        .description("REST API documentation for the Task Manager application. "
                                   + "Supports user and task management operations.")
                        .version("v1.0.0")                            // API version
                        .contact(new Contact()
                                .name("Task Manager Team")
                                .email("support@taskmanager.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server")));
    }
}
```

**What each part does:**
- `title` - The big heading shown in Swagger UI
- `description` - A paragraph explaining what the API does
- `version` - Helps consumers know which version they are using
- `contact` - Who to reach out to with questions
- `license` - Legal terms for using the API
- `servers` - Base URLs where the API is available (you can add staging/production URLs too)

### Step 3: Annotate Controllers

Annotations on your controllers add rich metadata to the documentation.

#### Controller-Level: `@Tag`

Groups related endpoints together in Swagger UI:

```java
@RestController
@RequestMapping("/tasks")
@Tag(name = "Task Management", description = "API endpoints for task management operations")
public class TaskController {
    // All endpoints in this controller appear under "Task Management" group
}
```

```java
@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "API endpoints for user management operations")
public class UserController {
    // All endpoints appear under "User Management" group
}
```

#### Method-Level: `@Operation`

Describes what an individual endpoint does:

```java
@PostMapping("/")
@Operation(
    summary = "Create a new task",              // Short one-line description
    description = "Creates a new task and assigns it to a user"  // Detailed explanation
)
public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
    // ...
}
```

#### Response Documentation: `@ApiResponse` / `@ApiResponses`

Documents every possible HTTP response:

```java
@PostMapping("/")
@ApiResponses(value = {
    @ApiResponse(
        responseCode = "201",
        description = "Task created successfully",
        content = @Content(schema = @Schema(implementation = TaskResponse.class))  // Shows response shape
    ),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (validation failed)"
    ),
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error"
    )
})
public ResponseEntity<TaskResponse> createTask(...) { ... }
```

This tells the frontend developer: "You will get a 201 with a TaskResponse body, or a 400 if validation fails, or a 500 if something goes wrong."

#### Parameter Documentation: `@Parameter`

Describes path variables, query parameters, and headers:

```java
@GetMapping("/{id}")
public ResponseEntity<TaskResponse> getTaskById(
        @Parameter(description = "Task ID", required = true)
        @PathVariable UUID id) {
    // ...
}

@GetMapping("/")
public ResponseEntity<PageResponse<TaskResponse>> getAllTasks(
        @Parameter(description = "Page number (starts from 0)")
        @RequestParam(defaultValue = "0") int page,

        @Parameter(description = "Number of items per page")
        @RequestParam(defaultValue = "10") int size,

        @Parameter(description = "Sort (format: field,direction, e.g. createdAt,desc)")
        @RequestParam(defaultValue = "createdAt,desc") String sort) {
    // ...
}
```

### Step 4: Annotate DTOs

DTOs get `@Schema` annotations that provide examples and descriptions for every field:

#### Request DTO Example

```java
@Schema(description = "Request to create a new task")
public class CreateTaskRequest {

    @NotNull(message = "Title is required")
    @Schema(
        description = "Task title",
        example = "Design new homepage",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String title;

    @Schema(
        description = "Task description",
        example = "Create wireframes and mockups for the new homepage design"
    )
    private String description;

    @NotNull(message = "Priority is required")
    @Schema(
        description = "Task priority level",
        example = "HIGH",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private TaskPriority priority;

    @NotNull(message = "Status is required")
    @Schema(
        description = "Task status",
        example = "PENDING",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private TaskStatus status;

    @NotNull(message = "User ID is required")
    @Schema(
        description = "ID of the user to assign the task to",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String userId;
}
```

#### Response DTO Example

```java
@Schema(description = "Task response model")
public class TaskResponse {

    @Schema(description = "Task ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Task title", example = "Design new homepage")
    private String title;

    @Schema(description = "Task priority", example = "HIGH")
    private String priority;

    @Schema(description = "Task status", example = "PENDING")
    private String status;

    @Schema(description = "Assigned user ID", example = "550e8400-e29b-41d4-a716-446655440001")
    private String userId;

    @Schema(description = "Assigned username", example = "johndoe")
    private String username;

    @Schema(description = "Whether the task is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Creation timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp", example = "2024-01-15T14:45:00")
    private LocalDateTime updatedAt;
}
```

**Why `example` matters:** When a frontend developer clicks "Try It Out" in Swagger UI, these example values pre-fill the request body. They can modify them and send a real request immediately.

---

## What We Gained

### 1. Auto-Generated Documentation
Documentation is generated directly from the code. When you add a new endpoint, it appears in Swagger UI automatically. When you change a parameter, the docs update. No manual work.

### 2. Interactive Testing (Try It Out)
Every endpoint in Swagger UI has a "Try It Out" button. Click it, fill in the parameters, click "Execute", and you get the real response from the running server. No Postman needed for quick testing.

**Example workflow:**
1. Open `http://localhost:8080/swagger-ui.html`
2. Expand "Task Management" section
3. Click `POST /tasks/` (Create a new task)
4. Click "Try It Out"
5. The request body is pre-filled with example values from `@Schema`
6. Click "Execute"
7. See the actual response: status code, headers, body

### 3. Frontend/Mobile Developer Experience
Frontend and mobile developers can now:
- See every endpoint at a glance
- Read parameter descriptions and validation rules
- See example request/response bodies
- Test endpoints without writing any code
- Generate client SDKs automatically from the OpenAPI spec

### 4. Standardization (OpenAPI 3.0)
The generated specification follows the OpenAPI 3.0 standard, which is understood by hundreds of tools:
- Postman can import it directly
- Code generators can create clients in any language
- API gateways can validate requests against it
- Monitoring tools can use it for API health checks

### 5. Quality & Testing
Having visual documentation makes it obvious when:
- An endpoint is missing proper error responses
- A parameter has no description
- A response body is not documented
- Validation rules are unclear

It acts as a living specification that keeps the team honest about API quality.

---

## Advantages

| Advantage | Description |
|---|---|
| **Always up-to-date** | Documentation is generated from code. Change the code, the docs change automatically. |
| **Interactive testing** | "Try It Out" replaces Postman for quick endpoint testing. |
| **Zero maintenance** | No separate documentation files to keep in sync. |
| **Standard format** | OpenAPI 3.0 is an industry standard supported by hundreds of tools. |
| **Client SDK generation** | Generate TypeScript, Python, Swift, Kotlin clients from the spec. |
| **Onboarding** | New developers understand the API in minutes, not hours. |
| **Validation visible** | `@NotNull`, `@Size`, `@Email` constraints are reflected in the docs. |
| **Example values** | Pre-filled examples make testing and understanding easy. |
| **Grouping** | `@Tag` organizes endpoints into logical sections. |
| **Free and open source** | SpringDoc is MIT-licensed with an active community. |

## Disadvantages

| Disadvantage | Description |
|---|---|
| **Additional dependency** | Adds SpringDoc to the classpath (though it is lightweight). |
| **Annotation overhead** | DTOs and controllers need extra annotations, adding verbosity to the code. |
| **Security risk in production** | If not disabled, anyone can see your full API structure. |
| **Limited customization** | Complex API patterns (e.g., polymorphic responses) can be hard to document accurately. |
| **Performance overhead** | Slight startup time increase for scanning annotations and generating the spec. |
| **Turkish/English mix** | Current annotations use Turkish descriptions; ideally they should be consistent. |
| **Learning curve** | Developers need to learn the annotation API (`@Schema`, `@Operation`, etc.). |

---

## Affected Files

| # | File | Change |
|---|---|---|
| 1 | `pom.xml` | Added `springdoc-openapi-starter-webmvc-ui` dependency |
| 2 | `OpenApiConfig.java` | New configuration class with API metadata |
| 3 | `TaskController.java` | Added `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` annotations |
| 4 | `UserController.java` | Added `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` annotations |
| 5 | `CreateTaskRequest.java` | Added `@Schema` annotations with descriptions and examples |
| 6 | `UpdateTaskRequest.java` | Added `@Schema` annotations with descriptions and examples |
| 7 | `SearchTaskRequest.java` | Added `@Schema` annotations with descriptions and examples |
| 8 | `CreateUserRequest.java` | Added `@Schema` annotations with descriptions and examples |
| 9 | `UpdateUserRequest.java` | Added `@Schema` annotations with descriptions and examples |
| 10 | `TaskResponse.java` | Added `@Schema` annotations with descriptions and examples |
| 11 | `UserResponse.java` | Added `@Schema` annotations with descriptions and examples |

---

## API Usage

### Accessing the Documentation

| Resource | URL | Description |
|---|---|---|
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` | Interactive visual documentation |
| **OpenAPI JSON** | `http://localhost:8080/v3/api-docs` | Machine-readable JSON specification |
| **OpenAPI YAML** | `http://localhost:8080/v3/api-docs.yaml` | Machine-readable YAML specification |

### Swagger UI Features

When you open Swagger UI, you will see:

1. **API Info Header** - Title, version, description, contact info, license
2. **Server Selection** - Dropdown to switch between environments (local, staging, prod)
3. **Endpoint Groups** - Organized by `@Tag`:
   - "Task Management" - All `/tasks` endpoints
   - "User Management" - All `/users` endpoints
4. **Each Endpoint Shows:**
   - HTTP method and path (color-coded: green=GET, blue=POST, orange=PUT, red=DELETE)
   - Summary and description
   - Parameters with types, descriptions, and required/optional markers
   - Request body schema with example values
   - Response codes with descriptions and response body schemas
5. **"Try It Out" Button** - On every endpoint:
   - Click "Try It Out"
   - Edit parameters and request body
   - Click "Execute"
   - See the actual curl command, response code, response headers, and response body

### Import into Postman

1. Open Postman
2. Click "Import"
3. Paste the URL: `http://localhost:8080/v3/api-docs`
4. Postman automatically creates a collection with all endpoints, parameters, and example bodies

### Generate Client SDKs

Using the OpenAPI Generator CLI, you can generate client libraries in any language:

```bash
# Generate a TypeScript/Axios client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g typescript-axios \
  -o ./generated-client/typescript

# Generate a Python client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g python \
  -o ./generated-client/python

# Generate a Swift client for iOS
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g swift5 \
  -o ./generated-client/swift

# Generate a Kotlin client for Android
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g kotlin \
  -o ./generated-client/kotlin
```

This means your frontend and mobile teams get a **type-safe, auto-generated API client** with zero manual work. When the API changes, regenerate the client and the compiler tells you what to update.

---

## Annotation Reference

| Annotation | Target | Purpose | Example |
|---|---|---|---|
| `@Tag` | Class (Controller) | Groups endpoints under a named section | `@Tag(name = "Task Management")` |
| `@Operation` | Method | Describes what an endpoint does | `@Operation(summary = "Create a new task")` |
| `@ApiResponse` | Method | Documents a single response code | `@ApiResponse(responseCode = "201", description = "Created")` |
| `@ApiResponses` | Method | Groups multiple `@ApiResponse` annotations | `@ApiResponses({@ApiResponse(...), @ApiResponse(...)})` |
| `@Parameter` | Parameter | Describes a path/query/header parameter | `@Parameter(description = "Task ID", required = true)` |
| `@Schema` | Class or Field | Describes a DTO or a field within a DTO | `@Schema(description = "Task title", example = "My Task")` |
| `@Content` | Inside `@ApiResponse` | Specifies the response body content type and schema | `@Content(schema = @Schema(implementation = TaskResponse.class))` |
| `@Hidden` | Class or Method | Hides an endpoint from the documentation | `@Hidden` on internal/admin endpoints |
| `@RequestBody` (OpenAPI) | Method | Customizes request body documentation | `@io.swagger.v3.oas.annotations.parameters.RequestBody(...)` |

### `@Schema` Common Attributes

| Attribute | Type | Description |
|---|---|---|
| `description` | String | Human-readable description of the field |
| `example` | String | Example value shown in Swagger UI and pre-filled in "Try It Out" |
| `requiredMode` | Enum | `REQUIRED` or `NOT_REQUIRED` - marks the field in the docs |
| `defaultValue` | String | Default value if the field is not provided |
| `allowableValues` | String[] | List of valid values (useful for enums) |
| `minLength` / `maxLength` | int | String length constraints |
| `minimum` / `maximum` | String | Numeric range constraints |
| `hidden` | boolean | If `true`, the field is excluded from the schema |
| `accessMode` | Enum | `READ_ONLY`, `WRITE_ONLY`, or `READ_WRITE` |

---

## Decision Log

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| **Documentation library** | SpringDoc OpenAPI | Springfox | Springfox has been abandoned (last release 2020, does not support Spring Boot 3.x). SpringDoc is actively maintained, supports Spring Boot 3.x natively, and follows the OpenAPI 3.0 standard. |
| **Description language** | Turkish + English mix | English only / Turkish only | Current state uses Turkish for Swagger descriptions (reflecting the team's primary language) and English for code. Future improvement: standardize to English for wider accessibility. |
| **Production exposure** | Currently enabled | Disabled in production | Should be disabled in production for security (see Performance & Security Notes below). Currently enabled for development convenience. |
| **Specification version** | OpenAPI 3.0 | OpenAPI 2.0 (Swagger) | OpenAPI 3.0 is the current industry standard. Better support for complex schemas, authentication schemes, and callbacks. |
| **Configuration approach** | Java Config (`@Bean`) | `application.yml` only | Java Config allows programmatic customization (conditional servers, dynamic descriptions). YAML is limited to simple key-value pairs. |
| **Where to put annotations** | Directly on controllers and DTOs | Separate OpenAPI YAML file | Annotations keep documentation next to the code, reducing the chance of them getting out of sync. A separate file would be more flexible but harder to maintain. |

---

## Future Improvements

### 1. JWT Authentication Scheme
When JWT authentication is added to the project, configure Swagger to include the "Authorize" button:

```java
@Bean
public OpenAPI taskManagerOpenAPI() {
    return new OpenAPI()
            .info(/* ... */)
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new Components()
                    .addSecuritySchemes("Bearer Authentication",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .bearerFormat("JWT")
                                    .scheme("bearer")
                                    .description("Enter your JWT token")));
}
```

This adds an "Authorize" button to Swagger UI. Click it, paste your JWT token, and all subsequent "Try It Out" requests include the `Authorization: Bearer <token>` header automatically.

### 2. API Versioning
If the API needs versioning (v1, v2), configure group-based documentation:

```java
// application.yml
springdoc:
  group-configs:
    - group: v1
      paths-to-match: /api/v1/**
    - group: v2
      paths-to-match: /api/v2/**
```

### 3. Custom Theme and Extensions
- Add a custom logo and color scheme to Swagger UI
- Add `x-` extension fields for custom metadata (e.g., rate limits, deprecation dates)
- Add request/response examples for error scenarios

### 4. Standardize Descriptions to English
Migrate all Turkish `@Schema` and `@Operation` descriptions to English for international team accessibility.

### 5. Add Detailed Error Response Schemas
Create standardized `ErrorResponse` schemas documenting the exact error format for 400, 404, 500 responses.

---

## Performance & Security Notes

### Security Risk

Swagger UI exposes your **entire API structure** - every endpoint, every parameter, every validation rule. In production, this is an attacker's dream. They can see every endpoint without guessing.

### Production Configuration (Disable Swagger)

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false    # Disables /v3/api-docs endpoint
  swagger-ui:
    enabled: false    # Disables /swagger-ui.html
```

Or, disable it conditionally using a Spring profile:

```java
@Configuration
@Profile("!prod")  // Only active when NOT in production
public class OpenApiConfig {
    @Bean
    public OpenAPI taskManagerOpenAPI() {
        // ... configuration ...
    }
}
```

### Development Configuration (Full Features)

```yaml
# application-dev.yml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    operations-sorter: method          # Sort endpoints by HTTP method
    tags-sorter: alpha                 # Sort groups alphabetically
    try-it-out-enabled: true           # Enable "Try It Out" by default
    filter: true                       # Add search/filter box
    display-request-duration: true     # Show how long each request took
```

### Performance Impact

| Aspect | Impact |
|---|---|
| **Startup time** | +200-500ms (one-time annotation scanning) |
| **Runtime performance** | Zero impact on API endpoints (docs are served separately) |
| **Memory usage** | +5-10MB for cached OpenAPI spec |
| **JAR size** | +2MB for SpringDoc dependency |

The overhead is negligible for development. In production, disabling it completely eliminates any impact.

---

## Conclusion

Adding OpenAPI/Swagger integration to the Task Manager project transforms API development from guesswork to precision. Instead of reading source code, guessing request formats, and maintaining outdated Markdown docs, the entire team now has:

- **A single source of truth** that is always accurate because it is generated from the code
- **An interactive playground** where anyone can test any endpoint in seconds
- **A machine-readable specification** that can generate client SDKs, Postman collections, and test suites
- **Visual documentation** that makes the API accessible to frontend developers, mobile developers, QA engineers, and even non-technical stakeholders

The investment is minimal: one dependency, one config class, and annotations on controllers and DTOs. The return is massive: every API consumer saves hours of time, every new team member onboards faster, and every API change is immediately visible.

**Before:** "Read the source code and figure it out."
**After:** Open `http://localhost:8080/swagger-ui.html` and see everything.
