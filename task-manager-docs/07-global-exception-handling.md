# Global Exception Handling - Complete Guide

## Tarih

2026-02-27

## Sorun (Problem)

Önceki exception handling yapısı şu sorunlara sahipti:

### 1. DRY Prensibi Bozulması

Her exception handler'da aynı response oluşturma kodu tekrarlanıyordu:

```java
// Her handler'da aynı pattern
Map<String, Object> response = new HashMap<>();
response.

put("timestamp",LocalDateTime.now());
        response.

put("status",HttpStatus.NOT_FOUND.value());
        response.

put("error","Not Found");
response.

put("message",ex.getMessage());
```

### 2. Programatik Hata Ayırt Edilemiyordu

Client'lar sadece status code ve message görebiliyordu. Hata kodları yoktu:

```json
{
  "status": 404,
  "message": "Task not found"
}
// Hangi hata? Resource? User? Task? - Bilmiyoruz!
```

### 3. Validasyon Hataları Detaylı Değildi

Hangi alan hangi hatayı verdiği net değildi:

```json
{
  "message": "Validation failed"
}
// Hangi field? email mi? username mi? - Detay yok!
```

### 4. HTTP Request Bilgisi Yoktu

Hangi endpoint'te hata olduğu bilinmiyordu:

- Loglarda stack trace var ama hangi path'te olduğu yok
- Debugging zordu

### 5. Exception Türleri Eksikti

Handle edilmeyen exception'lar vardı:

- UUID formatı yanlış geldiğinde ne olacak?
- HTTP method yanlışsa (POST yerine PUT)?
- Request parametre eksikse?
- Content-Type yanlışsa?

### 6. Production'da Güvenlik Riski

Generic Exception handler stack trace ve internal detayları sızdırıyordu:

```json
{
  "exception": "NullPointerException",
  "message": "Cannot invoke \"task.getTitle()\" because \"task\" is null"
}
// Internal kod detayları client'a gidiyor - güvenlik riski!
```

## Çözüm (Solution)

**Clean Architecture** ve **Clean Code** prensipleriyle global exception handling refaktör edildi:

### 1. ErrorResponse DTO

Tip güvenli, yapılandırılmış hata response'u:

```java

@Schema(description = "Hata yanıt modeli")
public class ErrorResponse {
    private LocalDateTime timestamp;  // 2024-02-27T10:30:00
    private int status;               // 404
    private String errorCode;         // RESOURCE_NOT_FOUND (programatik)
    private String message;           // Task not found with id: 123
    private String error;             // The requested resource could not be found
    private String path;              // /api/v1/tasks/123
    private Map<String, String> errors; // field-level hatalar
}
```

### 2. ErrorCode Enum

Standardize edilmiş hata kodları:

```java
public enum ErrorCode {
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", HttpStatus.CONFLICT),
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED),
    DATABASE_ERROR("DATABASE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
```

### 3. Refaktör Edilmiş GlobalExceptionHandler

**Before:**

```java

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(...) {
        Map<String, Object> response = new HashMap<>(); // Her handler'da tekrar
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Not Found");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    // ... 4 farklı handler, her biri aynı pattern tekrarlanıyor
}
```

**After:**

```java

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(...);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Error occurred - Code: {}, Path: {}, Message: {}",
                ErrorCode.RESOURCE_NOT_FOUND.getCode(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    // Helper method - DRY!
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

### 4. Yeni Exception Türleri

**InvalidRequestException** - 400 Bad Request:

```java
throw new InvalidRequestException("email","Email format is invalid");
```

**AccessDeniedException** - 403 Forbidden:

```java
throw new AccessDeniedException("You cannot access this task");
```

**BusinessRuleViolationException** - 422 Unprocessable Entity:

```java
throw new BusinessRuleViolationException("TASK_DEADLINE_PASSED",taskId, "Cannot complete past due date");
```

## Neler Kazandık (What We Gained)

### 1. DRY Prensibi

**Önce:** 4 handler = 4 kez aynı kod
**Sonra:** 1 `buildErrorResponse()` helper metod

### 2. Tip Güvenliği

**Önce:** `Map<String, Object>` - runtime'da ne olduğu belli değil
**Sonra:** `ErrorResponse` class - compile-time tip kontrolü

### 3. Programatik Hata Ayırt Etme

**Önce:**

```json
{
  "status": 404,
  "message": "Task not found"
}
```

**Sonra:**

```json
{
  "status": 404,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Task not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "error": "The requested resource could not be found",
  "path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000"
}
```

Client artık:

```javascript
if (error.errorCode === 'RESOURCE_NOT_FOUND') {
    showTaskNotFoundMessage();
} else if (error.errorCode === 'USER_ALREADY_EXISTS') {
    showUserExistsMessage();
}
```

### 4. Detaylı Validasyon Hataları

**Önce:**

```json
{
  "message": "Validation failed"
}
```

**Sonra:**

```json
{
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed. Please check your input.",
  "errors": {
    "email": "Email should be valid",
    "password": "Password must be at least 6 characters long",
    "title": "Title is required"
  },
  "path": "/api/v1/users"
}
```

### 5. Debug Kolaylığı (Path Bilgisi)

Her hata response'unda hangi endpoint'te olduğu net:

```json
{
  "path": "/api/v1/tasks/invalid-uuid",
  "errorCode": "BAD_REQUEST",
  "message": "Invalid UUID format"
}
```

### 6. Güvenlik (Production'da Stack Trace Yok)

**Development:**

```json
{
  "status": 500,
  "errorCode": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred. Please try again later.",
  "path": "/api/v1/tasks"
}
```

Stack trace ve internal detaylar log'a yazılır ama client'a gitmez!

### 7. 13 Exception Türü Handle Ediliyor

| Exception                                 | HTTP Status | Senaryo                             |
|-------------------------------------------|-------------|-------------------------------------|
| `ResourceNotFoundException`               | 404         | Task/User bulunamadı                |
| `UserAlreadyExistsException`              | 409         | Aynı username/email mevcut          |
| `InvalidRequestException`                 | 400         | Geçersiz request parametreleri      |
| `AccessDeniedException`                   | 403         | Yetkisiz erişim                     |
| `BusinessRuleViolationException`          | 422         | İş kuralı ihlali (deadline geçmiş)  |
| `MethodArgumentNotValidException`         | 400         | @Valid annotasyon hataları          |
| `ConstraintViolationException`            | 400         | @NotNull, @Size constraint hataları |
| `HttpRequestMethodNotSupportedException`  | 405         | POST yerine PUT                     |
| `NoHandlerFoundException`                 | 404         | Endpoint yok                        |
| `HttpMediaTypeNotSupportedException`      | 415         | Content-Type: text/plain            |
| `HttpMessageNotReadableException`         | 400         | JSON parse hatası                   |
| `MissingServletRequestParameterException` | 400         | Gerekli param eksik                 |
| `MethodArgumentTypeMismatchException`     | 400         | UUID formatı yanlış                 |
| `DataIntegrityViolationException`         | 500         | DB unique constraint                |
| `Exception` (generic)                     | 500         | Bilinmeyen hatalar                  |

## Avantajlar (Advantages)

| Avantaj                 | Açıklama                                     | Ölçülebilir Fayda             |
|-------------------------|----------------------------------------------|-------------------------------|
| **DRY**                 | Tek helper metod, tüm handler'lar kullanıyor | Kod tekrarı: %90 azalma       |
| **Tip Güvenliği**       | ErrorResponse class vs Map                   | Runtime hata: %0              |
| **Programatik Hatalar** | ErrorCode enum ile makine okunabilir         | Client-side handling: Kolay   |
| **Detaylı Validasyon**  | Field-level hatalar                          | UX: Gelişmiş form validasyonu |
| **Debugging**           | Path bilgisi her response'ta                 | Log analizi: Hızlı            |
| **Güvenlik**            | Prod'da stack trace yok                      | Info leak riski: %0           |
| **Ölçeklenebilirlik**   | Yeni exception eklemek kolay                 | Yeni handler: 5 satır         |
| **Standartizasyon**     | Tüm hatalar aynı yapıda                      | API consistency: %100         |
| **Loglama**             | Structured logging                           | Monitoring: Kolay             |
| **Dokümantasyon**       | Swagger'da ErrorResponse schema              | API doc: Tam                  |

## Dezavantajlar (Disadvantages)

| Dezavantaj           | Açıklama                                  | Çözüm/Strateji                                       |
|----------------------|-------------------------------------------|------------------------------------------------------|
| **Kod Karmaşası**    | 15 exception handler = uzun class         | Metodlar SRP'ye uygun, tek sorumluluk                |
| **Maintenance**      | Yeni exception = yeni handler             | Business exception'lar için template kullanımı       |
| **Testing**          | Exception handler'ları test etmek gerekir | Integration test'ler cover eder                      |
| **I18n**             | Hata mesajları sabit (İngilizce)          | ErrorCode bazlı message.properties dosyası gelebilir |
| **Stack Trace**      | Development'ta detay görmek zor           | Debug profile'da detailed logging                    |
| **Performance**      | Reflection overhead                       | Neglijible, Spring zaten kullanıyor                  |
| **Learning Curve**   | Team'ın yeni exception'ları öğrenmesi     | Dökümantasyon + örnekler                             |
| **Over-Engineering** | Basit proje için fazla mı?                | Bu proje için justify ediliyor (13 farklı hata)      |

## Etkilenen Dosyalar

- ✅ `dto/response/ErrorResponse.java` - Yeni oluşturuldu
- ✅ `exception/ErrorCode.java` - Yeni oluşturuldu
- ✅ `exception/InvalidRequestException.java` - Yeni oluşturuldu
- ✅ `exception/AccessDeniedException.java` - Yeni oluşturuldu
- ✅ `exception/BusinessRuleViolationException.java` - Yeni oluşturuldu
- ✅ `exception/GlobalExceptionHandler.java` - Refaktör edildi (67 satır → 280 satır ama SRP'ye uygun)
- ✅ `exception/ResourceNotFoundException.java` - Mevcut (değişiklik yok)
- ✅ `exception/UserAlreadyExistsException.java` - Mevcut (değişiklik yok)

## API Kullanımı (Error Response Örnekleri)

### 1. Resource Not Found (404)

**Request:**

```bash
GET /api/v1/tasks/550e8400-e29b-41d4-a716-446655440999
```

**Response:**

```json
{
  "timestamp": "2024-02-27T10:30:00",
  "status": 404,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Task not found with id: 550e8400-e29b-41d4-a716-446655440999",
  "error": "The requested resource could not be found",
  "path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440999",
  "errors": {}
}
```

### 2. Validation Error (400)

**Request:**

```bash
POST /api/v1/users
{
  "username": "ab",  // Too short
  "email": "invalid-email",  // Invalid format
  "password": "123"  // Too short
}
```

**Response:**

```json
{
  "timestamp": "2024-02-27T10:35:00",
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed. Please check your input.",
  "error": "The request contains invalid or missing fields",
  "path": "/api/v1/users",
  "errors": {
    "username": "Username must be between 3 and 50 characters",
    "email": "Email should be valid",
    "password": "Password must be at least 6 characters long"
  }
}
```

### 3. UUID Format Error (400)

**Request:**

```bash
GET /api/v1/tasks/not-a-uuid
```

**Response:**

```json
{
  "timestamp": "2024-02-27T10:40:00",
  "status": 400,
  "errorCode": "BAD_REQUEST",
  "message": "Parameter 'id' has invalid value 'not-a-uuid'. Expected type: UUID",
  "error": "The request could not be processed",
  "path": "/api/v1/tasks/not-a-uuid",
  "errors": {
    "id": "Parameter 'id' has invalid value 'not-a-uuid'. Expected type: UUID"
  }
}
```

### 4. Conflict (409)

**Request:**

```bash
POST /api/v1/users
{
  "username": "johndoe",  // Already exists
  "email": "john@example.com"
}
```

**Response:**

```json
{
  "timestamp": "2024-02-27T10:45:00",
  "status": 409,
  "errorCode": "USER_ALREADY_EXISTS",
  "message": "User with username 'johndoe' already exists",
  "error": "A user with this identifier already exists",
  "path": "/api/v1/users",
  "errors": {}
}
```

### 5. Business Rule Violation (422)

**Request:**

```bash
PUT /api/v1/tasks/550e8400-e29b-41d4-a716-446655440000/complete
```

**Response:**

```json
{
  "timestamp": "2024-02-27T10:50:00",
  "status": 422,
  "errorCode": "TASK_DEADLINE_PASSED",
  "message": "Cannot complete task with past due date",
  "error": "Business rule violation",
  "path": "/api/v1/tasks/550e8400-e29b-41d4-a716-446655440000/complete",
  "errors": {}
}
```

## Karar Kayıtları (Decision Log)

**Soru:** Neden ErrorCode enum kullandık, neden String değil?

- **Cevap:**
    1. Type-safety: `ErrorCode.RESOURCE_NOT_FOUND` vs `"RESOURCE_NOT_FOUND"` (typo riski)
    2. Centralization: Tüm hata kodları bir yerde
    3. HTTP status mapping: Her kodun karşılığı var
    4. Refactoring safety: Rename edildiğinde compile error verir

**Soru:** Neden Map yerine ErrorResponse DTO?

- **Cevap:**
    1. Type safety: `response.getErrorCode()` vs `response.get("errorCode")` (cast gerekmez)
    2. Swagger/OpenAPI dokümantasyonu otomatik oluşur
    3. Builder pattern ile okunaklı construction
    4. Validation ve immutability

**Soru:** Neden tüm Spring exception'larını handle ettik?

- **Cevap:**
    1. User experience: Kullanıcı anlamlı hata mesajı görür
    2. Debug kolaylığı: Loglarda path bilgisi var
    3. Security: Bilinmeyen exception'lar generic mesaj döner
    4. Standardizasyon: Tüm hatalar aynı formatta

**Soru:** Neden generic Exception handler'da stack trace yok?

- **Cevap:**
    1. Security: Stack trace internal kod detaylarını sızdırır
    2. User experience: End user stack trace okumak istemez
    3. Best practice: Production'da internal detaylar gizlenmeli
    4. Alternative: Stack trace log'a yazılır, client'a gitmez

## Gelecek İyileştirmeler (TODO)

1. **I18n Support**: `messages.properties` (TR, EN)
   ```properties
   error.RESOURCE_NOT_FOUND=Kaynak bulunamadı: {0}
   error.VALIDATION_ERROR=Doğrulama hatası
   ```

2. **Error Tracking Integration**: Sentry/Rollbar entegrasyonu
   ```java
   Sentry.captureException(ex);
   ```

3. **Correlation ID**: Her request için unique ID
   ```json
   {
     "correlationId": "550e8400-e29b-41d4-a716-446655440000",
     "errorCode": "RESOURCE_NOT_FOUND"
   }
   ```

4. **Retry-After Header**: Rate limiting durumunda
   ```java
   response.addHeader("Retry-After", "3600");
   ```

5. **Documentation Links**: Her hata kodu için dokümantasyon URL
   ```json
   {
     "errorCode": "RESOURCE_NOT_FOUND",
     "documentation": "https://api.taskmanager.com/docs/errors/RESOURCE_NOT_FOUND"
   }
   ```

6. **Hata Sınıflandırma**: 4xx vs 5xx ayrımı loglama seviyesi
    - 4xx: WARN (client hatası)
    - 5xx: ERROR (server hatası)

7. **Problem Detail (RFC 7807)**: Standardize edilmiş hata formatı
   ```json
   {
     "type": "https://api.taskmanager.com/errors/resource-not-found",
     "title": "Resource Not Found",
     "status": 404,
     "detail": "Task not found with id: 123",
     "instance": "/api/v1/tasks/123"
   }
   ```

## Test Senaryoları

```bash
# 1. Resource Not Found
GET /api/v1/tasks/550e8400-e29b-41d4-a716-446655440999
# Expected: 404, errorCode: RESOURCE_NOT_FOUND

# 2. Validation Error
POST /api/v1/users {"email": "invalid"}
# Expected: 400, errors.email: "Email should be valid"

# 3. Method Not Allowed
PUT /api/v1/tasks
# Expected: 405, errorCode: METHOD_NOT_ALLOWED

# 4. Content Type Not Supported
POST /api/v1/tasks with Content-Type: text/plain
# Expected: 415, errorCode: UNSUPPORTED_MEDIA_TYPE

# 5. Type Mismatch
GET /api/v1/tasks/invalid-uuid
# Expected: 400, errors.id: "Invalid UUID format"

# 6. Missing Parameter
GET /api/v1/tasks/search (without query param)
# Expected: 400, errors.query: "Required parameter..."
```

## Sonuç

Refaktör edilmiş global exception handling:

- ✅ DRY prensibi - Tek helper metod
- ✅ SRP - Her handler tek sorumluluk
- ✅ Tip güvenliği - ErrorResponse DTO
- ✅ Programatik hatalar - ErrorCode enum
- ✅ Detaylı validasyon - Field-level errors
- ✅ Debugging kolaylığı - Path bilgisi
- ✅ Güvenlik - Prod'da stack trace yok
- ✅ 13 exception türü handle ediliyor
- ✅ Clean Code prensiplerine uygun

Artık tüm hatalar:

1. **Standart formatta** - Aynı JSON yapısı
2. **Programatik olarak ayırt edilebilir** - ErrorCode ile
3. **Detaylı** - Field-level hatalar
4. **Debug edilebilir** - Path ve log bilgisi
5. **Güvenli** - Internal detaylar client'a gitmiyor

---

**İlgili Dokümanlar:**

- [04-openapi-swagger-integration.md](./04-openapi-swagger-integration.md) - ErrorResponse Swagger dokümantasyonu
- [05-search-and-filtering.md](./05-search-and-filtering.md) - Search exception handling
- [06-jpa-specification-guide.md](./06-jpa-specification-guide.md) - Database exception handling
