# Spring Security with JWT Authentication

## Date: 2026-03-02

---

## Problem

### Every Endpoint Was Wide Open

Before this change, our `SecurityConfig` looked like this:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                    .anyRequest().permitAll()
            );
    return http.build();
}
```

`.anyRequest().permitAll()` — that single line meant **every endpoint in the application was publicly accessible with zero authentication**. Anyone on the internet could:

```bash
# Delete any user — no login required
curl -X DELETE http://your-server.com/users/550e8400-e29b-41d4-a716-446655440000

# Delete any task — no login required
curl -X DELETE http://your-server.com/tasks/a1b2c3d4-e5f6-7890-abcd-ef1234567890

# Read all users including their emails — no login required
curl http://your-server.com/users/

# Read all tasks — no login required
curl http://your-server.com/tasks/
```

We had `spring-boot-starter-security` in `pom.xml` and a `BCryptPasswordEncoder` bean that hashed passwords on user creation. But that password was **never verified against anything**. Users could register with a password, but there was no login endpoint, no session, no token. The password was hashed and stored in the database, and that was the end of its life. Nobody ever checked it again.

### What Was Actually Missing

| Component | Status Before | Purpose |
|-----------|--------------|---------|
| Login endpoint | Missing | Let users authenticate with username + password |
| JWT token generation | Missing | Give authenticated users a token to include in subsequent requests |
| JWT token validation | Missing | Verify the token on every request |
| `UserDetailsService` | Missing | Tell Spring Security how to load a user from the database |
| Role/authority model | Missing | Control who can do what (USER vs ADMIN) |
| Stateless sessions | Not configured | API servers should not store session state |
| Endpoint protection | All `permitAll()` | Different endpoints need different access levels |

---

## Solution

We implemented **JWT (JSON Web Token) authentication**. Here is how it works at a high level:

```
1. User sends POST /auth/register  →  Server creates user, returns JWT token
   OR
   User sends POST /auth/login     →  Server verifies password, returns JWT token

2. User includes the token in every subsequent request:
   GET /tasks/  →  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

3. Server validates the token on every request:
   - Is the signature valid? (not tampered with)
   - Is the token expired?
   - Does the user still exist and is active?
   
4. If valid → process the request
   If invalid → return 401 Unauthorized
```

### Why JWT and Not Session-Based Authentication?

**Session-based authentication** stores user state on the server. The server creates a session, stores it in memory (or a database), and gives the client a session ID cookie. Every request sends the cookie, and the server looks up the session.

**JWT authentication** is **stateless**. The token itself contains all the information the server needs (username, role, expiration). The server doesn't store anything — it just verifies the token's signature.

| Aspect | Session-Based | JWT |
|--------|--------------|-----|
| Server state | Stored in memory/database | Nothing stored (stateless) |
| Scalability | Sticky sessions or shared session store needed | Any server can validate any token |
| Mobile apps | Cookies are awkward on mobile | Token in header works everywhere |
| Microservices | Session sharing across services is complex | Any service can validate the same token |
| REST compliance | Violates stateless constraint | Fully stateless |

For a REST API like ours, JWT is the standard choice.

---

## Implementation

### Files Created and Modified

| File | Status | Purpose |
|------|--------|---------|
| `entity/Role.java` | **New** | Enum with `USER` and `ADMIN` roles |
| `entity/User.java` | **Modified** | Now implements `UserDetails`, added `role` field |
| `security/JwtService.java` | **New** | Generates and validates JWT tokens |
| `security/JwtAuthenticationFilter.java` | **New** | Intercepts every request, extracts and validates JWT |
| `security/CustomUserDetailsService.java` | **New** | Loads users from database for Spring Security |
| `service/AuthService.java` | **New** | Handles register and login business logic |
| `controller/AuthController.java` | **New** | Exposes `/auth/register` and `/auth/login` |
| `dto/request/LoginRequest.java` | **New** | Login request DTO |
| `dto/response/AuthResponse.java` | **New** | Authentication response with token |
| `config/SecurityConfig.java` | **Rewritten** | Full JWT filter chain, endpoint protection |
| `pom.xml` | **Modified** | Added `jjwt-api`, `jjwt-impl`, `jjwt-jackson` |
| `application.yml` | **Modified** | Added `jwt.secret` and `jwt.expiration` |
| `V5__add_role_to_users.sql` | **New** | Flyway migration to add `role` column |

---

### Step 1: JWT Dependencies

We use the **JJWT library** (Java JWT) — the most widely used JWT library for Java:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

**Why three separate artifacts?** JJWT follows a clean architecture: `jjwt-api` contains the interfaces your code compiles against; `jjwt-impl` and `jjwt-jackson` are the implementation details that only need to exist at runtime. This is why `impl` and `jackson` have `<scope>runtime</scope>` — your code never imports them directly.

---

### Step 2: Role Enum and User Entity

**The `Role` enum:**

```java
public enum Role {
    USER,
    ADMIN
}
```

**Flyway migration (`V5__add_role_to_users.sql`):**

```sql
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
```

The `DEFAULT 'USER'` is critical — existing users in the database automatically get the `USER` role without any manual data migration.

**The `User` entity now implements `UserDetails`:**

Spring Security doesn't know how to work with our `User` entity by default. `UserDetails` is the interface Spring Security uses to represent an authenticated user. By implementing it, our `User` entity becomes directly usable by Spring Security's authentication system:

```java
@Entity
@Table(name = "users")
public class User implements UserDetails {

    // ... existing fields ...

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    // UserDetails interface methods:

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security expects "ROLE_" prefix for role-based authorization
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return isActive;  // Deactivated users can't authenticate
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }
}
```

**Why `"ROLE_" + role.name()`?** Spring Security has a convention: roles are stored with the `ROLE_` prefix. When you write `.hasRole("ADMIN")` in the security config, Spring internally checks for `ROLE_ADMIN`. If your authority doesn't have the prefix, `hasRole()` won't match. This is a common source of confusion.

**Why does `isEnabled()` return `isActive`?** When a user is soft-deleted (`isActive = false`), Spring Security should reject their login attempt. By returning `isActive` from `isEnabled()`, a deactivated user's JWT will fail validation even if the token itself hasn't expired.

---

### Step 3: JwtService — Token Generation and Validation

```java
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;    // Base64-encoded secret from application.yml

    @Value("${jwt.expiration}")
    private long expiration;     // Token lifetime in milliseconds (86400000 = 24 hours)

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())          // WHO this token belongs to
                .issuedAt(new Date())                        // WHEN the token was created
                .expiration(new Date(System.currentTimeMillis() + expiration))  // WHEN it expires
                .signWith(getSigningKey())                   // SIGN with our secret key
                .compact();                                   // Build the final JWT string
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);  // HMAC-SHA256 signing
    }
}
```

**What is a JWT token?** A JWT has three parts separated by dots:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lIiwiaWF0IjoxNzA5MzI5NjAwLCJleHAiOjE3MDk0MTYwMDB9.abc123signature
├── Header ──────────┤├── Payload (Claims) ──────────────────────────────────────────────────────┤├── Signature ────┤
```

- **Header**: Algorithm used (HS256) and token type (JWT)
- **Payload**: The data — username (`sub`), issued time (`iat`), expiration (`exp`)
- **Signature**: HMAC-SHA256 hash of (header + payload + secret key)

The signature ensures nobody can tamper with the payload. If someone changes the username in the payload, the signature won't match, and validation fails.

**Why HMAC-SHA256?** It's a symmetric algorithm — the same key signs and verifies. This is perfect for a single-service application like ours. For microservices where multiple services need to verify tokens but only one should sign them, you'd use RSA (asymmetric).

---

### Step 4: JwtAuthenticationFilter — Intercepting Every Request

This filter runs **before** every request reaches a controller. It checks if the request has a valid JWT token:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Get the Authorization header
        String authHeader = request.getHeader("Authorization");

        // 2. No header or wrong format? Let the request continue (it will hit permitAll
        //    or get rejected by the authorization rules)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extract the JWT token (remove "Bearer " prefix)
        String jwt = authHeader.substring(7);

        // 4. Extract username from the token
        String username = jwtService.extractUsername(jwt);

        // 5. Load the user from the database
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // 6. Validate the token
        if (jwtService.isTokenValid(jwt, userDetails)) {
            // 7. Set the authentication in Spring Security's context
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // 8. Continue the filter chain
        filterChain.doFilter(request, response);
    }
}
```

**Why `OncePerRequestFilter`?** Spring's filter chain can sometimes invoke a filter multiple times per request (e.g., when forwarding internally). `OncePerRequestFilter` guarantees the filter logic executes exactly once per request.

**Why check `SecurityContextHolder.getContext().getAuthentication() == null`?** If the user is already authenticated (by another filter or mechanism), we don't override it. This prevents unnecessary database lookups.

---

### Step 5: SecurityConfig — The Central Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public - no token needed
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Protected - valid token required
                        .requestMatchers(HttpMethod.GET, "/tasks/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/users/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/tasks/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/tasks/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/tasks/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/users/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/users/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/users/**").authenticated()

                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**Why `csrf(AbstractHttpConfigurer::disable)`?** CSRF (Cross-Site Request Forgery) protection is designed for browser-based applications that use cookies. Our API uses JWT tokens in the `Authorization` header — tokens are not automatically sent by the browser like cookies are, so CSRF attacks are not possible. Disabling CSRF is the standard practice for stateless REST APIs.

**Why `SessionCreationPolicy.STATELESS`?** This tells Spring Security to never create an HTTP session. Every request must carry its own JWT token. Without this, Spring would create sessions and store authentication state on the server, defeating the purpose of JWT.

**Why `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`?** This registers our JWT filter to run **before** Spring's default username/password filter. The order matters: our filter extracts the JWT, authenticates the user, and sets the `SecurityContext`. By the time Spring's built-in filter runs, the user is already authenticated and the filter is skipped.

---

### Step 6: AuthService and AuthController

**Registration flow:**

```
POST /auth/register
{
    "username": "johndoe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "password": "password123"
}

→ Response (201 Created):
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "johndoe",
    "role": "USER"
}
```

**Login flow:**

```
POST /auth/login
{
    "username": "johndoe",
    "password": "password123"
}

→ Response (200 OK):
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "johndoe",
    "role": "USER"
}
```

**Using the token:**

```bash
# Without token — 401 Unauthorized
curl http://localhost:8080/tasks/
# → 401

# With token — 200 OK
curl http://localhost:8080/tasks/ \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
# → [list of tasks]
```

---

## The Authentication Flow Step by Step

### Registration

```
Client                          AuthController              AuthService              Database
  │                                  │                          │                       │
  │ POST /auth/register              │                          │                       │
  │ {username, password, ...}        │                          │                       │
  │─────────────────────────────────>│                          │                       │
  │                                  │  register(request)       │                       │
  │                                  │─────────────────────────>│                       │
  │                                  │                          │ Check duplicates      │
  │                                  │                          │──────────────────────>│
  │                                  │                          │ BCrypt hash password  │
  │                                  │                          │ Save user with role   │
  │                                  │                          │──────────────────────>│
  │                                  │                          │                       │
  │                                  │                          │ Generate JWT token    │
  │                                  │  AuthResponse(token)     │                       │
  │                                  │<─────────────────────────│                       │
  │ 201 Created                      │                          │                       │
  │ {token, username, role}          │                          │                       │
  │<─────────────────────────────────│                          │                       │
```

### Authenticated Request

```
Client                     JwtFilter            UserDetailsService         SecurityContext        Controller
  │                           │                        │                        │                     │
  │ GET /tasks/               │                        │                        │                     │
  │ Authorization: Bearer xxx │                        │                        │                     │
  │──────────────────────────>│                        │                        │                     │
  │                           │ Extract username       │                        │                     │
  │                           │ from JWT token         │                        │                     │
  │                           │                        │                        │                     │
  │                           │ loadUserByUsername()   │                        │                     │
  │                           │───────────────────────>│                        │                     │
  │                           │  UserDetails           │                        │                     │
  │                           │<───────────────────────│                        │                     │
  │                           │                        │                        │                     │
  │                           │ Validate token         │                        │                     │
  │                           │ (signature + expiry)   │                        │                     │
  │                           │                        │                        │                     │
  │                           │ Set authentication     │                        │                     │
  │                           │───────────────────────────────────────────────>│                     │
  │                           │                        │                        │                     │
  │                           │ Continue filter chain  │                        │                     │
  │                           │────────────────────────────────────────────────────────────────────>│
  │                           │                        │                        │                     │
  │ 200 OK                    │                        │                        │                     │
  │ [tasks]                   │                        │                        │                     │
  │<──────────────────────────────────────────────────────────────────────────────────────────────│
```

---

## JWT Properties

```yaml
# application.yml
jwt:
  secret: dGFzay1tYW5hZ2VyLXNlY3JldC1rZXktZm9yLWp3dC10b2tlbi1nZW5lcmF0aW9uLTIwMjY=
  expiration: 86400000  # 24 hours in milliseconds
```

**`jwt.secret`** — A Base64-encoded string used to sign and verify JWT tokens. This must be at least 256 bits (32 bytes) for HMAC-SHA256. In production, this should come from an environment variable or a secrets manager, not from a YAML file committed to Git.

**`jwt.expiration`** — How long a token is valid. `86400000` milliseconds = 24 hours. After this, the user must log in again. Shorter expiration = more secure (less time for a stolen token to be used). Longer expiration = better user experience (less frequent logins).

---

## Endpoint Access Rules

| Endpoint | Method | Access |
|----------|--------|--------|
| `/auth/register` | POST | Public — anyone can register |
| `/auth/login` | POST | Public — anyone can log in |
| `/actuator/health` | GET | Public — Docker health check needs this |
| `/swagger-ui/**` | GET | Public — API documentation |
| `/v3/api-docs/**` | GET | Public — OpenAPI spec |
| `/tasks/**` | GET/POST/PUT/DELETE | Authenticated — valid JWT required |
| `/users/**` | GET/POST/PUT/DELETE | Authenticated — valid JWT required |

---

## Advantages

| Advantage | Explanation |
|-----------|-------------|
| **Stateless** | No server-side sessions; any server instance can validate any token |
| **Standard** | JWT is an open standard (RFC 7519) supported by every language and framework |
| **Scalable** | No session store to share between server instances |
| **Mobile-friendly** | Tokens in headers work on every client (browser, mobile, CLI) |
| **Self-contained** | Token carries the username and role — no database lookup on every request |
| **Expiration** | Tokens automatically expire after 24 hours |
| **Password security** | BCrypt hashing with proper verification via Spring Security's `AuthenticationManager` |
| **Soft-delete aware** | Deactivated users (`isActive = false`) are rejected by `UserDetails.isEnabled()` |

## Disadvantages

| Disadvantage | Explanation | Mitigation |
|--------------|-------------|------------|
| **Token can't be revoked** | Once issued, a JWT is valid until it expires | Use short expiration times; implement a token blacklist for logout if needed |
| **Token size** | JWT is larger than a session ID (~300 bytes vs ~32 bytes) | Negligible for HTTP headers |
| **Secret key management** | If the secret is compromised, all tokens can be forged | Use environment variables; rotate keys periodically |
| **No refresh token** | Users must log in again after 24 hours | Can be added later with a `/auth/refresh` endpoint |
| **Database hit per request** | `loadUserByUsername()` queries the database on every request | Can be cached with Spring Cache if needed |

---

## API Usage Examples

### Register a New User

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "password": "password123"
  }'
```

**Response (201 Created):**
```json
{
    "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJqb2huZG9lIiwiaWF0IjoxNzA5MzI5NjAwLCJleHAiOjE3MDk0MTYwMDB9.abc123",
    "username": "johndoe",
    "role": "USER"
}
```

### Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "password123"
  }'
```

**Response (200 OK):**
```json
{
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "username": "johndoe",
    "role": "USER"
}
```

### Login with Wrong Password

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "wrongpassword"
  }'
```

**Response (401 Unauthorized):**
```json
{
    "status": 401,
    "errorCode": "UNAUTHORIZED",
    "message": "Bad credentials"
}
```

### Access Protected Endpoint Without Token

```bash
curl http://localhost:8080/tasks/
```

**Response (401 Unauthorized)**

### Access Protected Endpoint With Token

```bash
curl http://localhost:8080/tasks/ \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

**Response (200 OK):**
```json
[
    {
        "id": "a1b2c3d4...",
        "title": "Fix login bug",
        "status": "IN_PROGRESS"
    }
]
```

---

## Affected Files

- [x] `pom.xml` — Added JJWT dependencies (jjwt-api, jjwt-impl, jjwt-jackson)
- [x] `entity/Role.java` — **New**: Enum with USER and ADMIN
- [x] `entity/User.java` — Implements `UserDetails`, added `role` field
- [x] `security/JwtService.java` — **New**: Token generation and validation
- [x] `security/JwtAuthenticationFilter.java` — **New**: Intercepts requests, validates JWT
- [x] `security/CustomUserDetailsService.java` — **New**: Loads users from database
- [x] `service/AuthService.java` — **New**: Register and login business logic
- [x] `controller/AuthController.java` — **New**: `/auth/register` and `/auth/login`
- [x] `dto/request/LoginRequest.java` — **New**: Login request DTO
- [x] `dto/response/AuthResponse.java` — **New**: Response with token, username, role
- [x] `dto/response/UserResponse.java` — Added `role` field
- [x] `config/SecurityConfig.java` — **Rewritten**: JWT filter chain, endpoint protection, stateless sessions
- [x] `application.yml` — Added `jwt.secret` and `jwt.expiration` properties
- [x] `V5__add_role_to_users.sql` — **New**: Flyway migration adding role column
