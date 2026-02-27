# Logging (Loglama) Rehberi - Temelden İleri Seviyeye

## Tarih
2026-02-27

## Log Nedir ve Neden Önemli?

**Log**, uygulamanın çalışması sırasında oluşan olayları, hataları ve bilgileri kaydeden metin kayıtlarıdır.

### Neden Log Tutuyoruz?

| Neden | Gerçek Senaryo |
|-------|---------------|
| **Debug** | "Task neden oluşturulmadı?" - Log'a bakıp parametreleri görmek |
| **Monitoring** | "Sistem yavaşladı mı?" - Response time loglarından anlamak |
| **Audit** | "Kim bu task'ı sildi?" - Audit loglarından kullanıcıyı bulmak |
| **Security** | "Şüpheli giriş denemesi mi var?" - Failed login loglarından tespit |
| **Error Tracking** | "Prod'da ne hatası aldık?" - Stack trace loglarından çözmek |
| **Performance** | "Hangi sorgu yavaş?" - Hibernate SQL loglarından optimize etmek |

### Log Seviyeleri (Levels)

```
DEBUG < INFO < WARN < ERROR
```

| Level | Ne Zaman? | Örnek |
|-------|-----------|-------|
| **DEBUG** | Geliştirme aşamasında detay | `TaskService: Creating task with title='Proje Planı'` |
| **INFO** | Bilgi, olay bildirimi | `TaskController: [POST] /tasks - 201 Created - 150ms` |
| **WARN** | Şüpheli ama kritik değil | `Slow query detected: 2.5s - SELECT * FROM tasks` |
| **ERROR** | Kritik hata, işlem başarısız | `DatabaseConnectionException: Could not connect to PostgreSQL` |

**Kural:** PROD ortamında DEBUG loglamayın! (Performans ve disk kullanımı)

## Logging Mimarisi

### 1. SLF4J + Logback (Spring Boot Standardı)

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TaskService {
    // Logger oluştur - class ismiyle
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    
    public void createTask(Task task) {
        log.debug("Creating task: {}", task.getTitle());  // DEBUG
        log.info("Task created successfully: id={}", task.getId());  // INFO
    }
}
```

**Neden SLF4J?**
- Facade pattern - Backend değişebilir (Logback, Log4j2, vs.)
- `{}` placeholder - String concatenation yapmaz (performans)
- Standart - Tüm Java projelerde aynı API

### 2. Log Formatları

#### Basit Format (DEV ortamı - okunabilir)
```
2024-02-27 14:30:15 DEBUG [main] TaskService - Creating task: Proje Planı
2024-02-27 14:30:15 INFO  [main] TaskController - [POST] /tasks - 201 Created - 150ms
2024-02-27 14:30:16 WARN  [main] TaskRepository - Slow query detected: 2.5s
2024-02-27 14:30:17 ERROR [main] GlobalExceptionHandler - Database error: Connection refused
```

#### Structured Format (PROD ortamı - makine okunabilir - JSON)
```json
{"timestamp":"2024-02-27T14:30:15.123+03:00","level":"INFO","thread":"http-nio-8080-exec-1","logger":"TaskController","correlationId":"a1b2c3d4","message":"Task created","durationMs":150,"method":"POST","path":"/tasks","status":201}
```

**Neden JSON?**
- Log aggregation tool'lar (ELK Stack, Splunk) parse edebilir
- Structured query yapılabilir: `level:ERROR AND path:/tasks`
- Correlation ID ile distributed tracing

### 3. Correlation ID (İzleme ID'si)

**Problem:** Bir request birden fazla servis geçiyor, hangi log hangi request'in?

**Çözüm:** Her request'e unique ID ata, tüm loglara yaz.

```
Request: GET /tasks
  → [a1b2c3d4] Controller: Received request
  → [a1b2c3d4] Service: Fetching tasks from DB
  → [a1b2c3d4] Repository: SELECT * FROM tasks
  → [a1b2c3d4] Controller: Response sent - 200ms
```

**Header'dan alınır:**
```
X-Correlation-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

## Ortam Bazlı Logging

### DEV (Geliştirme)

**Nereye:** Konsol (IntelliJ Console)
**Format:** Renkli, okunabilir
**Seviye:** DEBUG ve üstü
**Dosya:** Hayır (konsol yeterli)

```yaml
logging:
  level:
    root: INFO
    org.task.taskmaganer: DEBUG  # Kendi kodumuz DEBUG
    org.hibernate.SQL: DEBUG   # SQL sorguları görünsün
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE  # SQL parametreleri
```

**Log Çıktısı:**
```
🟢 2024-02-27 14:30:15 DEBUG TaskService - Creating task: Proje Planı
🟢 2024-02-27 14:30:15 DEBUG org.hibernate.SQL - insert into tasks (id, title, ...) values (?, ?, ...)
🟢 2024-02-27 14:30:15 TRACE BasicBinder - binding parameter [1] as [VARCHAR] - [Proje Planı]
🟢 2024-02-27 14:30:15 INFO  TaskController - Task created: id=550e8400-...
```

### TEST (Test/Test Otomasyon)

**Nereye:** Konsol + Dosya
**Format:** Yapılandırılmış
**Seviye:** INFO ve üstü
**Dosya:** `logs/task-manager.log`

```yaml
logging:
  level:
    root: INFO
    org.task.taskmaganer: DEBUG
  file:
    name: logs/task-manager.log
```

**Neden dosya?**
- CI/CD pipeline'ında test loglarını arşivlemek
- Test başarısız olursa logları incelemek

### PROD (Production)

**Nereye:** Sadece dosya (konsol yok - sunucu headless)
**Format:** JSON veya yapılandırılmış
**Seviye:** INFO ve üstü (DEBUG yasak!)
**Dosyalar:**
- `logs/task-manager.log` - Tüm INFO ve üstü
- `logs/task-manager-error.log` - Sadece ERROR
- `logs/task-manager-audit.log` - Audit event'ler

```yaml
logging:
  level:
    root: WARN  # Sadece WARN ve ERROR (üçüncü parti kütüphaneler)
    org.task.taskmaganer: INFO  # Kendi kodumuz INFO
    org.springframework.security: WARN
    org.hibernate: ERROR
  file:
    name: logs/task-manager.log
```

**Dosya Rotasyonu:**
```
logs/
├── task-manager.log              # Şu anki log
├── task-manager.2024-02-26.log   # Dünkü log (arşiv)
├── task-manager.2024-02-25.log   # Önceki gün
├── task-manager-error.log        # Sadece hatalar
└── task-manager-audit.log        # Audit trail
```

**Rotasyon Ayarları:**
- **Günlük:** Her gün yeni dosya
- **Saklama:** 90 gün (PROD), 30 gün (TEST)
- **Boyut:** Dosya 100MB olunca yeni dosya
- **Toplam Limit:** 50GB (disk dolmasın)

## Ne Loglanmalı, Ne Loglanmamalı?

### ✅ Logla (Güvenli)

```java
// İşlem başarılı
log.info("Task created | id={} | title={} | by={}", taskId, title, userId);

// İşlem başarısız
log.error("Failed to create task | title={} | error={}", title, ex.getMessage());

// Performance
log.info("Query executed | table={} | duration={}ms", "tasks", duration);

// Audit
auditLog.info("User login | username={} | ip={} | success={}", username, ip, success);
```

### ❌ Loglama (Güvenlik Riski)

```java
// HATALI - Şifreyi loglama!
log.info("User login | username={} | password={}", username, password);
// Log dosyasına: "User login | username=john | password=123456"

// HATALI - Kredi kartı bilgisi
log.info("Payment processed | card={} | amount={}", cardNumber, amount);
// PCI DSS violation - ceza alırsınız!

// HATALI - Token/Session ID
log.debug("Auth token: {}", jwtToken);
// Token çalınırsa impersonation attack

// HATALI - PII (Kişisel Bilgiler)
log.info("User registered | email={} | phone={} | tc={}", 
    email, phoneNumber, nationalId);
// GDPR violation - 20M€ ceza
```

### 🟡 Dikkatli Logla

```java
// Hassas ama masked loglanabilir
log.info("Card payment | card=****-****-****-1234 | amount={}", amount);

// Email domain'i logla, tamamını değil
log.info("User registered | domain={}", email.substring(email.indexOf('@')));
// Çıktı: "domain=@gmail.com"
```

## Log Formatı Best Practices

### 1. Structured Logging (Yapılandırılmış)

**Kötü (Parse edilemez):**
```
User john created task with title Proje Planı at 2024-02-27
```

**İyi (Parse edilebilir):**
```
Task created | id=550e8400-... | title=Proje Planı | by=john | at=2024-02-27T14:30:00
```

**En İyi (JSON):**
```json
{"event":"TASK_CREATED","id":"550e8400-...","title":"Proje Planı","userId":"john","timestamp":"2024-02-27T14:30:00"}
```

### 2. Context Bilgisi Ekle

```java
// Yetersiz
log.error("Error occurred");

// İdeal
log.error("Failed to update task | taskId={} | userId={} | error={} | stack={}", 
    taskId, userId, ex.getMessage(), ex);
```

### 3. Performance Metrics

```java
long start = System.currentTimeMillis();
// ... işlem ...
long duration = System.currentTimeMillis() - start;

if (duration > 1000) {
    log.warn("Slow operation | method={} | duration={}ms | threshold={}ms", 
        "createTask", duration, 1000);
} else {
    log.debug("Operation completed | method={} | duration={}ms", 
        "createTask", duration);
}
```

## AOP ile Otomatik Logging

**Problem:** Her controller metoduna elle `log.info()` yazmak zor.

**Çözüm:** Aspect-Oriented Programming (AOP) ile otomatik loglama.

```java
@Aspect
@Component
public class LoggingAspect {
    
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        
        // Request logla
        log.info("[{}] → {} {}", correlationId, request.getMethod(), request.getRequestURI());
        
        // Metodu çalıştır
        Object result = joinPoint.proceed();
        
        long duration = System.currentTimeMillis() - start;
        
        // Response logla
        log.info("[{}] ← {} {} - {}ms", correlationId, request.getMethod(), 
            request.getRequestURI(), duration);
        
        return result;
    }
}
```

**Sonuç:** Tüm REST endpoint'leri otomatik loglanır.

## Audit Logging (İş Kaydı)

**Normal Log:** Sistem olayları (hata, performans)
**Audit Log:** Business olayları (kim ne yapmış?)

```java
@Service
public class AuditLogService {
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    public void logTaskCreated(String taskId, String title, String userId) {
        MDC.put("action", "CREATE");
        MDC.put("entity", "TASK");
        
        auditLog.info("Task created | id={} | title={} | by={}", taskId, title, userId);
    }
}
```

**Audit Log Çıktısı:**
```
2024-02-27 14:30:00 AUDIT [http-nio-8080-exec-1] Task created | id=550e8400-... | title=Proje Planı | by=john.doe
2024-02-27 14:35:00 AUDIT [http-nio-8080-exec-2] User login | username=john.doe | ip=192.168.1.100 | success=true
2024-02-27 14:40:00 AUDIT [http-nio-8080-exec-3] Task deleted | id=550e8400-... | title=Proje Planı | by=admin
```

## Loglama Standartları (Checklist)

### Development (DEV)
- [ ] Konsola renkli loglar
- [ ] DEBUG seviyesi açık
- [ ] SQL sorguları görünür
- [ ] Korrelation ID var

### Test (TEST)
- [ ] Konsol + dosya logları
- [ ] INFO seviyesi
- [ ] CI/CD'de log arşivleniyor
- [ ] Test fail olunca loglara bakılabiliyor

### Production (PROD)
- [ ] Sadece dosya (konsol yok)
- [ ] INFO seviyesi (DEBUG kapalı)
- [ ] JSON veya yapılandırılmış format
- [ ] Log rotation var (günlük, 90 gün saklama)
- [ ] Ayrı error log dosyası
- [ ] Ayrı audit log dosyası
- [ ] Şifre/token/PII loglanmıyor
- [ ] Correlation ID var (distributed tracing için)
- [ ] Slow query loglanıyor (>1s)

## Gerçek Senaryolar

### Senaryo 1: Production'da Hata

**Problem:** Customer "Task oluşturamıyorum" diyor.

**Debug:**
```bash
# 1. Log dosyasına bak
tail -f logs/task-manager-error.log

# 2. Correlation ID bul
grep "550e8400-e29b-41d4-a716-446655440000" logs/task-manager.log

# 3. Tüm request akışını gör
# [a1b2c3d4] → POST /tasks
# [a1b2c3d4] TaskService: Creating task...
# [a1b2c3d4] ERROR: Database connection failed
# [a1b2c3d4] ← POST /tasks - 500ms - 500 Error
```

### Senaryo 2: Performance Problemi

**Problem:** Task listesi yavaş yükleniyor.

**Debug:**
```bash
# Slow query'leri bul
grep "Slow query" logs/task-manager.log | tail -20

# Sonuç:
# 2024-02-27 WARN TaskRepository - Slow query detected: 2.5s - SELECT * FROM tasks
# 2024-02-27 WARN TaskRepository - Slow query detected: 3.1s - SELECT * FROM tasks
```

**Çözüm:** Pagination eklenmeli, index konulmalı.

### Senaryo 3: Security Audit

**Problem:** Şüpheli kullanıcı aktivitesi.

**Debug:**
```bash
# Audit log'dan tüm işlemleri bul
grep "john.doe" logs/task-manager-audit.log

# Sonuç:
# 14:00:00 User login | username=john.doe | ip=192.168.1.100
# 14:05:00 Task viewed | id=111 | by=john.doe
# 14:10:00 Task deleted | id=222 | by=john.doe  ← Şüpheli!
# 14:15:00 User logout | username=john.doe
```

## Karar Ağacı: Hangi Seviyede Loglayayım?

```
Ne logluyorum?
├── Debug/Development bilgisi?
│   └── DEBUG → SQL sorgusu, parametre değerleri
├── İşlem başarılı/olay?
│   ├── Business event (kim ne yaptı)?
│   │   └── AUDIT → User login, Task created
│   └── Sistem event?
│       └── INFO → Request/response, Cache hit/miss
├── Şüpheli ama kritik değil?
│   └── WARN → Slow query, Retry attempt, Deprecation
└── Hata/İşlem başarısız?
    ├── Recoverable (retry edilebilir)?
    │   └── WARN → Timeout, Connection reset
    └── Fatal (crash olur)?
        └── ERROR → DB down, NullPointer
```

## Özet

**Loglama = Uygulamanın Sesi**

**Golden Rules:**
1. **DEV:** DEBUG açık, konsola yaz, renkli, okunabilir
2. **TEST:** INFO seviyesi, konsol + dosya
3. **PROD:** INFO/WARN/ERROR, sadece dosya, JSON format, rotation
4. **Never log:** Password, token, credit card, PII
5. **Always include:** Correlation ID, timestamp, level, context
6. **Audit separate:** Business events ayrı log dosyasına
7. **Monitor:** Slow queries, error rate, unusual activity

**Loglama = Debugging + Monitoring + Audit + Security bir arada!**

---

**İlgili Dosyalar:**
- `src/main/resources/logback-spring.xml` - Logback konfigürasyonu
- `src/main/java/org/task/taskmaganer/config/LoggingAspect.java` - AOP loglama
- `src/main/java/org/task/taskmaganer/service/AuditLogService.java` - Audit loglama
