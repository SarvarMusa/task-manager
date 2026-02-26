# Enum Kullanımı - TaskPriority ve TaskStatus

## Tarih
2026-02-26

## Sorun (Problem)

Task entity'sinde `priority` ve `status` alanları **String** tipinde tanımlanmıştı. Bu durum şu sorunlara yol açıyordu:

1. **Geçersiz Değerler**: Client isteği `"priority": "SUPER_HIGH"` veya `"status": "DONE"` gönderebilirdi - bu değerler veritabanına kaydedilirdi ama business logic'te beklenmeyen davranışlara neden olurdu

2. **Case Sensitivity**: `"high"`, `"HIGH"`, `"High"` - hepsi farklı string değerler, tutarsız veri oluşurdu

3. **Validasyon Eksikliği**: `@Size(max=20)` sadece uzunluk kontrolü yapıyordu, içeriği kontrol etmiyordu

4. **Dokümantasyon Eksikliği**: API kullanıcıları hangi değerleri kullanabileceklerini bilmiyordu

5. **IDE Desteği Yok**: Developer'lar hangi değerlerin geçerli olduğunu göremiyordu, sürekli kod okumak zorundaydı

## Çözüm (Solution)

**TaskPriority** ve **TaskStatus** enum'ları oluşturuldu:

```java
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
```

Entity, DTO, Controller, Repository ve Service katmanlarında String → Enum değişimi yapıldı.

## Neler Kazandık (What We Gained)

### 1. Tip Güvenliği (Type Safety)
- Derleme zamanında hata yakalama
- Sadece tanımlı enum değerleri kullanılabilir
- Null safety artışı

### 2. Otomatik Validasyon
- Spring, invalid enum değerleri için **otomatik 400 Bad Request** döndürür
- Manuel validasyon kodu yazmaya gerek kalmadı
- `@NotNull` ile null kontrolü yapılabilir

### 3. Case-Insensitive Destek
- `PENDING`, `pending`, `Pending` - hepsi aynı enum değerine dönüştürülür
- Client farklı formatlarda gönderebilir

### 4. IDE ve Auto-complete Desteği
- Developer'lar `TaskPriority.` yazınca tüm seçenekleri görür
- Refactoring kolaylığı (değer değişince tüm kod otomatik güncellenir)

### 5. Swagger/OpenAPI Dokümantasyonu
```yaml
priority:
  type: string
  enum: [LOW, MEDIUM, HIGH]
  description: Task priority level
```

### 6. Business Logic Kolaylığı
```java
// Önce (String - hata riski)
if (task.getPriority().equals("high")) { ... }

// Sonra (Enum - tip güvenli)
if (task.getPriority() == TaskPriority.HIGH) { ... }
```

## Avantajlar (Advantages)

| Avantaj | Açıklama |
|---------|----------|
| **Veri Tutarlılığı** | Veritabanında sadece geçerli değerler bulunur |
| **Hata Önleme** | Invalid değerler API seviyesinde reddedilir |
| **Kod Okunabilirliği** | `TaskPriority.HIGH` vs `"high"` - çok daha net |
| **Maintenance Kolaylığı** | Yeni değer eklemek enum'a bir satır eklemek kadar kolay |
| **Switch-case Kullanımı** | Enum ile exhaustive switch-case yapılabilir |
| **Database Storage** | `@Enumerated(EnumType.STRING)` ile okunabilir format |
| **Test Kolaylığı** | Mock ve test data oluşturmak daha kolay |

## Dezavantajlar (Disadvantages)

| Dezavantaj | Açıklama | Çözüm |
|------------|----------|-------|
| **Veritabanı Boyutu** | String olarak saklanırsa VARCHAR gerektirir (ORDINAL daha az yer kaplar) | EnumType.STRING kullanılıyor (okunabilirlik > performans) |
| **Yeni Değer Ekleme** | Production'da yeni değer eklemek migration gerektirir | Veritabanında enum değişikliği yok, sadece kodda ekleme |
| **JSON Serileştirme** | Enum → String dönüşümü gerektirir | TaskResponse'te `.name()` kullanılarak çözüldü |
| **Complex Queries** | Native SQL'de enum kullanımı zor olabilir | JPQL kullanılarak çözüldü |
| **Backward Compatibility** | Eğer client eski String değer gönderirse 400 hatası alır | Client'ın API dokümantasyonuna göre çalışması gerekir |

## Etkilenen Dosyalar

- ✅ `entity/TaskPriority.java` - Yeni oluşturuldu
- ✅ `entity/TaskStatus.java` - Yeni oluşturuldu
- ✅ `entity/Task.java` - `priority` ve `status` alanları Enum oldu
- ✅ `dto/request/CreateTaskRequest.java` - Validasyon güncellendi
- ✅ `dto/request/UpdateTaskRequest.java` - String → Enum
- ✅ `dto/response/TaskResponse.java` - Enum → String dönüşümü
- ✅ `repository/TaskRepository.java` - Query parametreleri güncellendi
- ✅ `controller/TaskController.java` - PathVariable'lar Enum oldu
- ✅ `service/TaskService.java` - Metod imzaları güncellendi

## Örnek API Kullanımı

### Önce (String)
```json
{
  "title": "Yeni Görev",
  "priority": "high",        // Büyük/küçük harf sorunu
  "status": "in-progress"    // Tire vs underscore karışıklığı
}
```

### Sonra (Enum)
```json
{
  "title": "Yeni Görev",
  "priority": "HIGH",        // Standart format
  "status": "IN_PROGRESS"    // Net ve belgeli
}
```

## Hata Mesajları

### Geçersiz Değer Gönderildiğinde
```json
{
  "timestamp": "2026-02-26T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Failed to convert 'INVALID' to TaskStatus"
}
```

## Sonuç

Enum kullanımı, API'mizin **robustness**'ını artırdı, **validasyon** ihtiyacını azalttı ve **developer experience**'ı iyileştirdi. Dezavantajları, kazanılan faydalara göre minimal ve çözülebilir düzeydedir.
