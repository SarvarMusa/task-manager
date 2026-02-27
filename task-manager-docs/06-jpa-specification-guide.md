# JPA Specification Rehberi - Ne, Ne Zaman, Nasıl?

## Tarih
2026-02-27

## Specification Nedir?

**JPA Specification**, Spring Data JPA'nın dinamik ve esnek sorgular yazmanı sağlayan bir pattern. SQL'deki `WHERE` koşullarını Java kodunda obje olarak temsil etmeni sağlar.

**Basit Analoji:**
> Specification = Filtre şablonu
> 
> SQL'de: `WHERE status = 'ACTIVE' AND priority = 'HIGH'`
> 
> Java'da: `Specification.where(withStatus(ACTIVE)).and(withPriority(HIGH))`

## Ne Zaman Kullanmalısın?

### ✅ Kullan (Kullanman Gereken Senaryolar)

| Senaryo | Neden? | Örnek |
|---------|--------|-------|
| **Dinamik Filtreler** | Kaç parametre gönderileceği belli değil | Search ekranı: status, priority, user, date range (hepsi opsiyonel) |
| **Opsiyonel Kriterler** | Her filtre isteğe bağlı | Dashboard: "Sadece HIGH priority göster" ama bazen tüm priority'ler |
| **Tarih Aralığı** | Between sorguları | "Bu ay oluşturulan görevler", "Son 7 gün" |
| **Çoklu Kombinasyon** | Farklı filtre kombinasyonları | Status=PENDING + User=John + Priority=HIGH |
| **Complex Query** | AND/OR karışık | `(title LIKE '%x%' OR desc LIKE '%x%') AND status = 'ACTIVE'` |
| **Runtime Query** | Sorgu çalışma zamanında oluşuyor | Admin paneli: Kullanıcı hangi filtreleri seçtiyse o query oluşur |

### ❌ Kullanma (Alternatif Daha İyi Olan Senaryolar)

| Senaryo | Neden Kullanma? | Alternatif | Örnek |
|---------|-----------------|------------|-------|
| **Sabit Query** | Specification overhead'a gerek yok | `@Query` veya Derived Query | `findByStatus(TaskStatus status)` |
| **Tek Kriter** | Basit derived query yeterli | `findByUserId(UUID userId)` | Kullanıcının tüm görevleri |
| **Native SQL** | Complex join'ler, window functions | `@Query(nativeQuery = true)` | Raporlama sorguları |
| **Aggregation** | GROUP BY, SUM, COUNT | `@Query` veya JDBC | "Her kullanıcının görev sayısı" |
| **Full-Text Search** | LIKE yavaş, index kullanamaz | Elasticsearch | "Milyonlarca kayıtta arama" |

## Hangi Sorunlara Çözüm?

### 1. Method Explosion (Metod Patlaması)

**Sorun:**
```java
// Her kombinasyon için ayrı metod yazmak zorunda
findByStatus(Status s)
findByPriority(Priority p)
findByStatusAndPriority(Status s, Priority p)
findByUserId(UUID userId)
findByUserIdAndStatus(UUID userId, Status s)
findByUserIdAndPriority(UUID userId, Priority p)
findByUserIdAndStatusAndPriority(UUID userId, Status s, Priority p)
// ... 20+ metod daha
```

**Specification Çözümü:**
```java
// Tek metod - tüm kombinasyonları destekler
Page<Task> findAll(Specification<Task> spec, Pageable pageable);

// Kullanım
spec = withStatus(ACTIVE).and(withPriority(HIGH))
spec = withUserId(userId).and(withStatus(PENDING))
spec = withPriority(LOW)  // Sadece priority
```

### 2. Null Check Karmaşası

**Sorun:**
```java
@Query("SELECT t FROM Task t WHERE " +
       "(:status IS NULL OR t.status = :status) AND " +
       "(:priority IS NULL OR t.priority = :priority) AND " +
       "(:userId IS NULL OR t.user.id = :userId)")
// Query karışık, okunması zor
```

**Specification Çözümü:**
```java
public static Specification<Task> withStatus(Status status) {
    return (root, query, cb) -> 
        status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
}
// Null check specification içinde gizli, kullanım temiz
```

### 3. Dinamik AND/OR Kombinasyonu

**Sorun:**
```java
// Runtime'da kullanıcı hangi filtreleri seçecek bilmiyorsun
// Query string dinamik oluşturmak güvenlik riski (SQL Injection)
String sql = "SELECT * FROM tasks WHERE 1=1";
if (status != null) sql += " AND status = '" + status + "'";
if (priority != null) sql += " AND priority = '" + priority + "'";
// SQL Injection riski! + Kod karmaşık
```

**Specification Çözümü:**
```java
Specification<Task> spec = Specification.where(null);
if (status != null) spec = spec.and(withStatus(status));
if (priority != null) spec = spec.and(withPriority(priority));
if (userId != null) spec = spec.and(withUserId(userId));
// Type-safe, SQL Injection yok, okunaklı
```

### 4. Date Range ve Complex Criteria

**Sorun:**
```java
@Query("SELECT t FROM Task t WHERE t.createdAt BETWEEN :from AND :to " +
       "AND (:status IS NULL OR t.status = :status) " +
       "AND (t.title LIKE %:search% OR t.description LIKE %:search%)")
// Her yeni kriter için query'yi değiştirmek zorunda
```

**Specification Çözümü:**
```java
// Her kriter bağımsız, birleştirmek kolay
Specification<Task> spec = withCreatedAtBetween(from, to)
    .and(withStatus(status))
    .and(withSearchQuery(search));
// Yeni kriter eklemek için sadece .and() ekle
```

## Örnek Senaryolar

### Senaryo 1: E-Ticaret Ürün Filtreleme

```java
// Kullanıcı seçimi:
// - Kategori: Elektronik
// - Fiyat: 1000-5000 TL
// - Marka: Apple veya Samsung
// - Stokta var
// - Sıralama: Fiyat (artan)

Specification<Product> spec = withCategory("Elektronik")
    .and(withPriceBetween(1000, 5000))
    .and(withBrandIn(Arrays.asList("Apple", "Samsung")))
    .and(withStockGreaterThan(0));

Page<Product> products = productRepo.findAll(spec, 
    PageRequest.of(0, 20, Sort.by("price").ascending()));
```

**Neden Specification?**
- Her filtre opsiyonel (kullanıcı fiyat seçmeyebilir)
- Çoklu kategori ve marka desteği
- Fiyat aralığı dinamik (slider'dan gelen değer)

### Senaryo 2: Admin Panel Raporlama

```java
// Admin seçimi:
// - Tarih aralığı: Son 30 gün
// - Durum: Sadece ERROR olanlar
// - Kullanıcı: Belirli bir user veya tümü
// - Metin: Log mesajında "payment" geçenler

Specification<Log> spec = withTimestampBetween(last30Days, now)
    .and(withLevel(LogLevel.ERROR))
    .and(userId != null ? withUserId(userId) : null)
    .and(withMessageContaining("payment"));

List<Log> errors = logRepo.findAll(spec);
```

**Neden Specification?**
- Admin bazen user filtrelemek istemez (null)
- Tarih aralığı her zaman dinamik
- Farklı raporlar için farklı kombinasyonlar

### Senaryo 3: Task Manager (Bu Proje)

```java
// Dashboard arama:
// - Metin: "proje toplantı"
// - Status: IN_PROGRESS
// - Priority: HIGH veya MEDIUM
// - Son kullanma: Bu hafta
// - Aktif: true

var criteria = TaskFilterCriteria.builder()
    .searchQuery("proje toplantı")
    .status(IN_PROGRESS)
    .priority(HIGH)
    .dueDateFrom(today)
    .dueDateTo(endOfWeek)
    .isActive(true)
    .build();

Specification<Task> spec = TaskSpecification.withFilters(criteria);
```

**Neden Specification?**
- 8 farklı filtre (hepsi opsiyonel)
- Kombinasyon sayısı: 2^8 = 256 farklı sorgu
- 256 metod yazmak yerine 8 specification + 1 composite

## Specification vs Alternatifler

| Durum | Specification | @Query | Derived Query | Native SQL | Elasticsearch |
|-------|--------------|--------|---------------|------------|---------------|
| **Dinamik Filtreler** | ✅ Harika | ❌ Zor | ❌ Metod patlaması | ⚠️ Riskli | ✅ Harika |
| **Sabit Query** | ⚠️ Overhead | ✅ İdeal | ✅ Basit | ❌ Gereksiz | ❌ Ağır |
| **Complex Join** | ⚠️ Zor | ✅ İdeal | ❌ Limitli | ✅ İdeal | ⚠️ Mapping zor |
| **Full-Text Search** | ❌ LIKE yavaş | ❌ LIKE yavaş | ❌ LIKE yavaş | ⚠️ Index? | ✅ Harika |
| **Aggregation** | ❌ GROUP BY yok | ✅ SUM/COUNT | ❌ Yok | ✅ Harika | ✅ Harika |
| **Öğrenme Eğrisi** | ⚠️ Orta | ✅ Kolay | ✅ Kolay | ✅ Kolay | ⚠️ Yüksek |
| **Maintenance** | ✅ Kolay | ⚠️ SQL string | ✅ Kolay | ⚠️ SQL string | ⚠️ Cluster |

## Best Practices

### 1. Her Specification Tek Sorumluluk (SRP)

```java
// ✅ DO
public static Specification<Task> withStatus(TaskStatus status) {
    return (root, query, cb) -> 
        status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
}

public static Specification<Task> withPriority(TaskPriority priority) {
    return (root, query, cb) -> 
        priority == null ? cb.conjunction() : cb.equal(root.get("priority"), priority);
}

// ❌ DON'T - Tek specification çok kriter
public static Specification<Task> withFilters(Status s, Priority p, UUID u, ...) {
    // Çok karmaşık, SRP'yi bozar
}
```

### 2. Composite Specification'lar

```java
// ✅ DO - Küçük parçaları birleştir
Specification<Task> spec = Specification.where(withActiveStatus())
    .and(withStatus(status))
    .and(withPriority(priority));

// ❌ DON'T - Monolithic specification
Specification<Task> spec = (root, query, cb) -> {
    // 50 satır logic, okunması zor, test edilemez
};
```

### 3. Null Handling

```java
// ✅ DO - Her specification kendi null check'ini yapar
public static Specification<Task> withStatus(Status status) {
    return (root, query, cb) -> {
        if (status == null) return cb.conjunction();  // WHERE 1=1 (etkisiz)
        return cb.equal(root.get("status"), status);
    };
}

// Kullanım: spec.and(withStatus(null)) -> sorun yok
```

### 4. Type Safety (Field Constants)

```java
// ✅ DO - Entity'de field constants
@Entity
public class Task {
    public static final class Fields {
        public static final String status = "status";
        public static final String priority = "priority";
        // ...
    }
}

// Kullanım
cb.equal(root.get(Task.Fields.status), status);  // Refactor-safe

// ❌ DON'T - String literals
cb.equal(root.get("status"), status);  // Rename edilince kırılır
```

## Karar Ağacı (Specification Kullanmalı mıyım?)

```
Sorgunda parametre var mı?
├── Hayır → Derived Query (findByStatus)
├── Evet → Parametre sabit mi?
│   ├── Evet → @Query
│   └── Evet → Kaç parametre?
│       ├── 1-2 → Derived Query
│       └── 3+ → SPECIFICATION ✅
└── Evet → Parametreler opsiyonel mi?
    ├── Hayır → @Query
    └── Evet → Kombinasyon çok mu?
        ├── Hayır → @Query + null checks
        └── Evet → SPECIFICATION ✅
```

## Özet

**Specification Kullan:**
- ✅ 3+ opsiyonel parametre varsa
- ✅ Filtre kombinasyonu çoksa (2^n)
- ✅ Runtime'da query dinamik oluşuyorsa
- ✅ Tarih aralığı veya range sorguları varsa
- ✅ Complex AND/OR mantığı varsa

**Specification Kullanma:**
- ❌ Sadece 1-2 sabit parametre
- ❌ Query hiç değişmiyor
- ❌ Aggregation (GROUP BY) gerekiyor
- ❌ Full-text search performans kritik
- ❌ Native SQL özellikleri gerekli (window functions)

## Sonuç

Specification, **dinamik sorguların** ve **opsiyonel filtrelerin** olduğu durumlarda kurtarıcıdır. Eğer kullanıcı arayüzünde 3+ filtre varsa ve bunlar isteğe bağlıysa, Specification kullan. Basit sorgular için Specification overkill'dir.

Bu proje (Task Manager) Specification için **mükemmel bir örnektir**:
- 8 farklı filtre (hepsi opsiyonel)
- Kombinasyon: 256 farklı sorgu
- Tarih aralığı filtreleri
- Full-text search

Eğer Specification kullanmasaydık, 256 metod veya karmaşık `@Query` string'ler yazmak zorunda kalırdık!

---

**İlgili Dokümanlar:**
- [05-search-and-filtering.md](./05-search-and-filtering.md) - Specification implementasyon detayları
- [02-pagination-implementation.md](./02-pagination-implementation.md) - Specification + Pagination birlikte kullanımı
