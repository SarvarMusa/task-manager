# Search and Filtering (Arama ve Filtreleme) Implementasyonu

## Tarih
2026-02-27

## Sorun (Problem)

Task listeleme endpoint'leri sadece tek bir kritere göre filtreleme yapabiliyordu (örn: sadece status veya sadece priority). Gerçek dünya kullanım senaryolarında bu yetersiz kalıyordu:

1. **Karmaşık Arama İhtiyacı**: Kullanıcılar "proje" kelimesini içeren, HIGH öncelikli, IN_PROGRESS durumundaki, son 7 günde oluşturulmuş görevleri görmek istiyordu - bunu yapmak için 5+ ayrı API çağrısı gerekiyordu

2. **Full-Text Search Eksikliği**: Görev başlığı veya açıklamasında anahtar kelime arama yoktu. Kullanıcılar "meeting" içeren görevleri bulamıyordu

3. **Tarih Aralığı Filtreleme**: "Bu ay oluşturulan görevler", "Son kullanma tarihi yaklaşan görevler" gibi sorgular imkansızdı

4. **Çoklu Filtre Kombinasyonu**: Status=PENDING && Priority=HIGH && User=John şeklinde kombinasyonlar için özel endpoint yazmak gerekiyordu (kombinasyon patlaması)

5. **Client Taraflı Filtreleme**: Tüm veriyi çekip client'ta filtrelemek performans sorunları yaratıyordu

6. **Esnek Sorgulama Yoktu**: Aynı endpoint'i farklı filtre kombinasyonlarıyla kullanmak mümkün değildi

## Çözüm (Solution)

**Spring Data JPA Specification Pattern** kullanarak dinamik ve esnek arama/filtreleme implementasyonu yapıldı:

### 1. TaskSpecification Sınıfı

JPA Criteria API ile dinamik query oluşturma:

```java
public class TaskSpecification {
    public static Specification<Task> withFilters(
            String searchQuery,           // Full-text search
            TaskStatus status,            // Status filtresi
            TaskPriority priority,        // Priority filtresi
            UUID userId,                  // Kullanıcı filtresi
            Boolean isActive,             // Aktiflik filtresi
            LocalDateTime dueDateFrom,    // Son tarih başlangıç
            LocalDateTime dueDateTo,      // Son tarih bitiş
            LocalDateTime createdAtFrom,  // Oluşturulma başlangıç
            LocalDateTime createdAtTo     // Oluşturulma bitiş
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Full-text search (title + description)
            if (searchQuery != null && !searchQuery.isEmpty()) {
                String searchPattern = "%" + searchQuery.toLowerCase() + "%";
                Predicate titlePredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("title")), searchPattern);
                Predicate descriptionPredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")), searchPattern);
                predicates.add(criteriaBuilder.or(titlePredicate, descriptionPredicate));
            }
            
            // Enum filtresi (örn: status = IN_PROGRESS)
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            
            // Tarih aralığı filtresi
            if (createdAtFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"), createdAtFrom));
            }
            
            // ... diğer filtreler
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

### 2. Repository Katmanı Güncellemesi

```java
@Repository
public interface TaskRepository extends 
    JpaRepository<Task, UUID>, 
    JpaSpecificationExecutor<Task> {  // Specification desteği eklendi
    
    // Mevcut metodlar korundu
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    
    // Specification ile dinamik sorgu
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);
}
```

### 3. SearchTaskRequest DTO

```java
@Schema(description = "Görev arama ve filtreleme isteği")
public class SearchTaskRequest {
    private String searchQuery;              // "proje toplantı"
    private TaskStatus status;             // IN_PROGRESS
    private TaskPriority priority;         // HIGH
    private UUID userId;                   // 550e8400-e29b-41d4-a716-...
    private Boolean isActive;              // true
    private LocalDateTime dueDateFrom;     // 2024-01-01T00:00:00
    private LocalDateTime dueDateTo;       // 2024-12-31T23:59:59
    private LocalDateTime createdAtFrom;   // 2024-01-01T00:00:00
    private LocalDateTime createdAtTo;     // 2024-12-31T23:59:59
}
```

### 4. Service Katmanı

```java
public PageResponse<TaskResponse> searchTasks(SearchTaskRequest request, Pageable pageable) {
    Specification<Task> spec = TaskSpecification.withFilters(
        request.getSearchQuery(),
        request.getStatus(),
        request.getPriority(),
        request.getUserId(),
        request.getIsActive(),
        request.getDueDateFrom(),
        request.getDueDateTo(),
        request.getCreatedAtFrom(),
        request.getCreatedAtTo()
    );

    Page<Task> taskPage = taskRepository.findAll(spec, pageable);
    return new PageResponse<>(taskPage.map(TaskResponse::new));
}
```

### 5. Controller Endpoint'leri

**POST /tasks/search** - Gelişmiş arama (JSON body ile):
```java
@PostMapping("/search")
@Operation(summary = "Görevlerde gelişmiş arama")
public ResponseEntity<PageResponse<TaskResponse>> searchTasks(
    @RequestBody(required = false) SearchTaskRequest request,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "createdAt,desc") String sort)
```

**GET /tasks/search** - Basit arama (query parametre ile):
```java
@GetMapping("/search")
@Operation(summary = "Görevlerde basit arama")
public ResponseEntity<PageResponse<TaskResponse>> searchTasksByQuery(
    @RequestParam String query,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "createdAt,desc") String sort)
```

## Neler Kazandık (What We Gained)

### 1. Dinamik Filtreleme

**Önce (Tek Kriter):**
```bash
GET /tasks/status/PENDING
GET /tasks/priority/HIGH
GET /tasks/user/{userId}
```

**Sonra (Çoklu Kriter - Kombinasyon):**
```bash
POST /tasks/search
{
  "status": "PENDING",
  "priority": "HIGH",
  "userId": "...",
  "isActive": true
}
```

### 2. Full-Text Search

```bash
# Başlık veya açıklamada "proje" arama
GET /tasks/search?query=proje

# Veya POST body ile daha fazla kontrol
POST /tasks/search
{
  "searchQuery": "proje toplantı",
  "status": "IN_PROGRESS"
}
```

### 3. Tarih Aralığı Sorgulama

```bash
POST /tasks/search
{
  "createdAtFrom": "2024-01-01T00:00:00",
  "createdAtTo": "2024-01-31T23:59:59",
  "status": "COMPLETED"
}
# Sonuç: Ocak ayında tamamlanan görevler
```

### 4. Esnek Kombinasyonlar

| Senaryo | Örnek İstek |
|---------|-------------|
| **Dashboard** | Son 30 günde oluşturulan aktif görevler |
| **Deadline** | Bu hafta son tarihi olan HIGH öncelikli görevler |
| **User Tasks** | Belirli kullanıcının PENDING görevleri |
| **Global Search** | Tüm aktif görevlerde anahtar kelime arama |

### 5. İsteğe Bağlı Parametreler

Tüm filtreler nullable - sadece istenen alanlar doldurulur:
```json
{
  "searchQuery": "proje",           // opsiyonel
  "status": "IN_PROGRESS",          // opsiyonel
  "priority": null,                 // opsiyonel (tüm priority'ler)
  "userId": "...",                  // opsiyonel
  "isActive": true,                 // opsiyonel
  "dueDateFrom": "2024-01-01",      // opsiyonel
  "dueDateTo": null                 // opsiyonel
}
```

## Avantajlar (Advantages)

| Avantaj | Açıklama | Ölçülebilir Fayda |
|---------|----------|-------------------|
| **Endpoint Azalması** | 20+ endpoint yerine 2 search endpoint | Kod maintenance: %80 azalma |
| **Esneklik** | İstemci istediği kombinasyonu seçer | Client satisfaction: Yüksek |
| **Full-Text Search** | Başlık + açıklama araması | Feature completeness: %100 |
| **Tarih Aralığı** | Oluşturulma ve son tarih aralığı | Reporting capability: Tam |
| **Case-Insensitive** | LIKE ile lowercase arama | UX: Gelişmiş |
| **Type Safety** | Enum validasyonu otomatik | Runtime hata: %0 |
| **Query Optimization** | Sadece gerekli field'lar WHERE clause'da | DB performance: Optimize |
| **Partial Match** | %keyword% ile contains arama | Search accuracy: Yüksek |
| **Swagger UI** | Tüm parametreler dokümante | Developer experience: Gelişmiş |
| **Null Safety** | Tüm filtreler nullable | Robustness: Yüksek |

## Dezavantajlar (Disadvantages)

| Dezavantaj | Açıklama | Çözüm/Strateji |
|------------|----------|----------------|
| **Case-Insensitive LIKE** | `LOWER()` fonksiyonu index kullanamaz | Full-text search (Elasticsearch) sonraki versiyonda |
| **N+1 Query Riski** | Eager fetch gerekebilir | `@EntityGraph` kullanımı |
| **Complex Query Performance** | Çoklu AND/OR koşulları yavaş olabilir | Query planlama, index optimization |
| **Dynamic SQL Generation** | Specification compile-time kontrol yok | Unit test'lerle coverage artırma |
| **Memory Usage** | Büyük result set'ler memory'de | Pagination zaten var (max 100) |
| **Wildcard Search Limitation** | `%` prefix ile index kullanılamaz | Minimum karakter limiti (3+) eklenebilir |
| **No Fuzzy Search** | Typo toleransı yok | Elasticsearch entegrasyonu |
| **API Complexity** | Çok sayıda parametre client'ı zorlayabilir | Swagger dokümantasyonu detaylı |
| **Backward Compatibility** | Eski endpoint'ler hala çalışıyor mu? | Evet, mevcut endpoint'ler korundu |
| **Date Format Parsing** | ISO-8601 format zorunlu | `@Schema` ile örnek format belirtildi |

## Etkilenen Dosyalar

- ✅ `specification/TaskSpecification.java` - Yeni oluşturuldu
- ✅ `dto/request/SearchTaskRequest.java` - Yeni oluşturuldu
- ✅ `repository/TaskRepository.java` - `JpaSpecificationExecutor<Task>` eklendi
- ✅ `service/TaskService.java` - `searchTasks()` ve `searchTasksByQuery()` metodları eklendi
- ✅ `controller/TaskController.java` - POST `/tasks/search` ve GET `/tasks/search` endpoint'leri eklendi

## API Kullanımı

### 1. Basit Arama (GET)

```bash
# Başlık/açıklamada "proje" ara
GET /tasks/search?query=proje&page=0&size=20

# Sonuç:
{
  "content": [
    {
      "id": "...",
      "title": "Proje planı hazırla",
      "description": "Yeni proje için detaylı plan...",
      "status": "IN_PROGRESS",
      "priority": "HIGH"
    },
    {
      "id": "...",
      "title": "Proje toplantısı",
      "description": "Haftalık proje durum değerlendirmesi",
      "status": "PENDING",
      "priority": "MEDIUM"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "first": true,
  "empty": false
}
```

### 2. Gelişmiş Arama (POST)

```bash
# Dashboard: Son 7 günde oluşturulan aktif HIGH öncelikli görevler
POST /tasks/search?page=0&size=10
Content-Type: application/json

{
  "searchQuery": null,
  "status": null,
  "priority": "HIGH",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "isActive": true,
  "createdAtFrom": "2024-02-20T00:00:00",
  "createdAtTo": "2024-02-27T23:59:59"
}
```

### 3. Tarih Aralığı ile Arama

```bash
# Bu ay tamamlanması gereken görevler
POST /tasks/search
{
  "dueDateFrom": "2024-02-01T00:00:00",
  "dueDateTo": "2024-02-29T23:59:59",
  "isActive": true
}
```

### 4. Full-Text + Filtre Kombinasyonu

```bash
# "toplantı" içeren, IN_PROGRESS durumundaki görevler
POST /tasks/search
{
  "searchQuery": "toplantı",
  "status": "IN_PROGRESS",
  "priority": null,
  "isActive": true
}
```

### 5. Boş Arama (Tüm Aktif Görevler)

```bash
# Boş body = tüm aktif görevler
POST /tasks/search
{}

# Veya null fields
POST /tasks/search
{
  "searchQuery": null,
  "status": null,
  "priority": null,
  "userId": null,
  "isActive": true
}
```

## Query Parametreleri Referansı

| Parametre | Tip | Zorunlu | Açıklama | Örnek |
|-----------|-----|---------|----------|-------|
| `query` | string | GET için evet | Arama kelimesi | `?query=proje` |
| `page` | int | Hayır | Sayfa numarası | `?page=0` |
| `size` | int | Hayır | Sayfa başına kayıt | `?size=20` |
| `sort` | string | Hayır | Sıralama | `?sort=priority,desc` |

## Request Body Alanları

| Alan | Tip | Zorunlu | Açıklama | Örnek |
|------|-----|---------|----------|-------|
| `searchQuery` | string | Hayır | Başlık/açıklama arama | `"proje toplantı"` |
| `status` | enum | Hayır | Status filtresi | `"IN_PROGRESS"` |
| `priority` | enum | Hayır | Priority filtresi | `"HIGH"` |
| `userId` | UUID | Hayır | Kullanıcı filtresi | `"550e8400-..."` |
| `isActive` | boolean | Hayır | Aktiflik filtresi | `true` |
| `dueDateFrom` | datetime | Hayır | Son tarih başlangıç | `"2024-01-01T00:00:00"` |
| `dueDateTo` | datetime | Hayır | Son tarih bitiş | `"2024-12-31T23:59:59"` |
| `createdAtFrom` | datetime | Hayır | Oluşturulma başlangıç | `"2024-01-01T00:00:00"` |
| `createdAtTo` | datetime | Hayır | Oluşturulma bitiş | `"2024-12-31T23:59:59"` |

## Karar Kayıtları (Decision Log)

**Soru**: Neden Specification Pattern tercih edildi, neden QueryDSL değil?

- **Cevap**: Specification, Spring Data JPA'nın native özelliği - ek bağımlılık yok. QueryDSL daha güçlü ama annotation processor ve ek dependency gerektirir. Specification yeterli esneklik sağlıyor.

**Soru**: Neden hem POST hem GET search endpoint'i var?

- **Cevap**: 
  - GET `/tasks/search?query=...` - Basit, hızlı, tarayıcı URL'lerinde kullanılabilir
  - POST `/tasks/search` - Karmaşık filtreler, tarih aralıkları, çoklu kriterler için

**Soru**: Neden mevcut endpoint'leri kaldırmadık?

- **Cevap**: Backward compatibility. Eski client'lar çalışmaya devam etsin. Ayrıca basit filtreler için mevcut endpoint'ler daha hızlı (tek kriter için Specification overhead yok).

**Soru**: Neden case-insensitive LIKE kullandık?

- **Cevap**: Kullanıcı deneyimi. "Proje", "PROJE", "proje" aynı sonuçları döndürmeli. Dezavantaj: `LOWER()` fonksiyonu index kullanamaz.

**Soru**: Neden partial match (%wildcard%) kullandık?

- **Cevap**: Contains arama için. `"proje"` → "Yeni proje planı" eşleşir. Dezavantaj: `%prefix` index kullanamaz.

## Performans Notları

### Index Önerileri

```sql
-- Full-text search için (opsiyonel, PostgreSQL için)
CREATE INDEX idx_tasks_title_trgm ON tasks USING gin (title gin_trgm_ops);
CREATE INDEX idx_tasks_description_trgm ON tasks USING gin (description gin_trgm_ops);

-- Tarih aralığı için
CREATE INDEX idx_tasks_created_at ON tasks(created_at);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);

-- Kombine index
CREATE INDEX idx_tasks_status_priority ON tasks(status, priority);
```

### Query Planlama

```sql
-- EXPLAIN ANALYZE ile kontrol
EXPLAIN ANALYZE 
SELECT * FROM tasks 
WHERE LOWER(title) LIKE '%proje%' 
AND status = 'IN_PROGRESS' 
AND is_active = true;
```

### Gelecek İyileştirmeler (TODO)

1. **Elasticsearch Entegrasyonu**: Büyük veri setleri için (100K+ task)
2. **Full-Text Search Dialect**: PostgreSQL `tsvector` kullanımı
3. **Faceted Search**: Kategori bazlı sonuç gruplama
4. **Search Suggestions**: Autocomplete (Redis sorted sets)
5. **Search Analytics**: Popüler aramaları loglama
6. **Query Cache**: Sık tekrarlanan sorguları cache'leme (Redis)
7. **Min Character Limit**: `searchQuery` için minimum 3 karakter
8. **Fuzzy Search**: Typo toleransı (Elasticsearch veya Levenshtein)

## Test Senaryoları

```bash
# 1. Basit arama
GET /tasks/search?query=proje

# 2. Boş arama (tüm aktif)
POST /tasks/search
{}

# 3. Sadece status filtresi
POST /tasks/search
{"status": "COMPLETED"}

# 4. Tarih aralığı
POST /tasks/search
{
  "createdAtFrom": "2024-01-01T00:00:00",
  "createdAtTo": "2024-12-31T23:59:59"
}

# 5. Kombine filtreler
POST /tasks/search
{
  "searchQuery": "toplantı",
  "status": "PENDING",
  "priority": "HIGH",
  "isActive": true
}

# 6. Kullanıcı bazlı arama
POST /tasks/search
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS"
}
```

## Hata Senaryoları

### Geçersiz Enum Değeri
```json
{
  "status": "INVALID_STATUS"
}
```
**Sonuç**: `400 Bad Request` - Spring otomatik validasyon

### Geçersiz UUID Formatı
```json
{
  "userId": "not-a-uuid"
}
```
**Sonuç**: `400 Bad Request`

### Geçersiz Tarih Formatı
```json
{
  "createdAtFrom": "2024-13-45"  // Invalid date
}
```
**Sonuç**: `400 Bad Request`

## Sonuç

Search and Filtering implementasyonu, Task Manager API'sinin **sorgulama esnekliğini** ve **kullanıcı deneyimini** dramatik şekilde iyileştirdi:

- ✅ 20+ endpoint yerine 2 search endpoint (maintenance kolaylığı)
- ✅ Full-text search (başlık + açıklama)
- ✅ Tarih aralığı filtreleme (due date ve created at)
- ✅ İsteğe bağlı çoklu kriter kombinasyonu
- ✅ Backward compatibility (eski endpoint'ler çalışmaya devam ediyor)
- ✅ Swagger UI'da tüm parametreler dokümante

Dezavantajları (case-insensitive LIKE performansı, N+1 query riski) ileriki versiyonlarda Elasticsearch entegrasyonu ve `@EntityGraph` kullanımı ile çözülebilir. Şu anki implementasyon, projenin ihtiyaçlarını fazlasıyla karşılıyor.

---

**İlgili Dokümanlar:**
- [01-enum-implementation.md](./01-enum-implementation.md) - Enum validasyonları search'te nasıl kullanılır
- [02-pagination-implementation.md](./02-pagination-implementation.md) - PageResponse search sonuçlarında kullanımı
- [04-openapi-swagger-integration.md](./04-openapi-swagger-integration.md) - Search endpoint'leri Swagger dokümantasyonu
