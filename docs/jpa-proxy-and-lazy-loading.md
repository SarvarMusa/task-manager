# JPA/Hibernate Proxy ve Lazy Loading

## Proxy Nedir?

**Proxy** (Türkçe: vekil/temsilci), Hibernate tarafından oluşturulan **sahte bir entity nesnesidir**. 

Gerçek veriyi tutmaz, sadece veritabanına erişim yeteneği olan bir "yer tutucu" görevi görür.

## Neden Proxy Kullanılır?

Performance için. `@ManyToOne(fetch = FetchType.LAZY)` ilişkilerde:

```java
@Entity
public class Task {
    @ManyToOne(fetch = FetchType.LAZY)  // LAZY = Proxy kullan
    private User user;
}
```

Bu durumda `Task` yüklenirken `User` **hemen yüklenmez**. Yerine bir **UserProxy** oluşturulur.

## Proxy Nasıl Çalışır?

### 1. Sorgu Anı (Transaction Açık)

```java
Task task = taskRepository.findById(taskId);  
// Veritabanı: SELECT * FROM tasks WHERE id = ?
// task.user = UserProxy@129 (içi boş, sadece id bilgisi var)
```

**Bellek durumu:**
- `task` → Gerçek Task objesi (dolu)
- `task.user` → UserProxy (boş, sadece id tutar)

### 2. Proxy Üzerinden Erişim

```java
// Transaction hala açık
String username = task.getUser().getUsername();  
// Proxy: "Veritabanına gidip gerçek User'ı yüklemeliyim"
// Veritabanı: SELECT * FROM users WHERE id = ?
// Şimdi proxy yerini gerçek User'a bırakır
```

### 3. Transaction Kapandıktan Sonra (HATA!)

```java
@Transactional
public Task getTask(UUID id) {
    return taskRepository.findById(id);  // Transaction burada kapanır
}

// Başka metod veya thread'de:
Task task = taskService.getTask(id);
String username = task.getUser().getUsername();  
// 💥 LazyInitializationException veya "Cannot evaluate proxy"
```

**Neden hata verir?**
- Proxy veritabanı bağlantısı ister
- Ama transaction kapalı → bağlantı yok
- Proxy erişemez → exception fırlatır

## Karşılaştırma Tablosu

| Durum | LAZY (Proxy) | EAGER |
|-------|-------------|-------|
| İlk sorgu | Hızlı (sadece Task) | Yavaş (Task + User JOIN) |
| Bellek kullanımı | Düşük (başta) | Yüksek (hepsi yüklenir) |
| Sonradan erişim | Ekstra sorgu | Gerekmez |
| Transaction sonrası | ❌ Hata verir | ✅ Çalışır |
| Use case | Her zaman User'a ihtiyaç yok | Her zaman User lazım |

## Gerçek Hayattan Örnek

**Senaryo:** Sipariş (Order) ve Müşteri (Customer)

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;
}
```

### Kullanım 1: Sadece Sipariş Listesi
```java
// Sadece siparişler lazım, müşteri bilgisi değil
List<Order> orders = orderRepository.findAll();  
// 1 sorgu: SELECT * FROM orders
// Customer = proxy (yükleme yok, hızlı)
```

### Kullanım 2: Sipariş + Müşteri Bilgisi
```java
// Müşteri ismi de lazım
for (Order order : orders) {
    System.out.println(order.getCustomer().getName());  
    // Her erişimde: SELECT * FROM customers WHERE id = ?
    // N+1 problemi oluşur!
}
```

**N+1 Problemi:** 100 sipariş için → 1 (siparişler) + 100 (müşteriler) = 101 sorgu!

## Çözümler

### 1. EntityGraph ile Önceden Yükleme (Önerilen)

Aynı sorguda ilişkili entity'yi de çek:

```java
@Entity
@NamedEntityGraph(
    name = "Task.withUser",
    attributeNodes = @NamedAttributeNode("user")
)
public class Task { ... }

// Repository'de kullanım:
@EntityGraph(value = "Task.withUser")
@Query("SELECT t FROM Task t WHERE t.isActive = true")
Page<Task> findAllActiveTasks(Pageable pageable);
```

**Ne olur?**
```sql
-- Tek sorguda her ikisi de gelir
SELECT t.*, u.* 
FROM tasks t 
LEFT JOIN users u ON t.user_id = u.id 
WHERE t.is_active = true
```

### 2. JOIN FETCH

```java
@Query("SELECT t FROM Task t JOIN FETCH t.user WHERE t.isActive = true")
List<Task> findAllWithUsers();
```

### 3. Transaction Sınırını Genişlet

```java
@Transactional(readOnly = true)  // Metod seviyesinde
public List<TaskResponse> getActiveTasks() {
    // Tüm işlemler transaction içinde kalır
}
```

### 4. DTO ile Dönüşüm (En Performanslı)

```java
@Query("SELECT new org.example.TaskDTO(t.id, t.title, u.username) " +
       "FROM Task t JOIN t.user u WHERE t.isActive = true")
List<TaskDTO> findAllActiveTaskDTOs();
```

**Avantaj:** Sadece gerekli alanlar çekilir, proxy kullanılmaz.

## Hata Mesajları ve Anlamları

| Hata Mesajı | Anlamı | Çözüm |
|------------|--------|-------|
| `LazyInitializationException` | Transaction kapandıktan sonra proxy'e erişildi | EntityGraph, JOIN FETCH veya @Transactional kullan |
| `Could not initialize proxy` | Session kapalı, proxy initialize edilemez | DTO kullan veya fetch stratejisi değiştir |
| `org.hibernate.LazyInitializationException` | Hibernate spesifik hatası | `@ManyToOne(fetch = EAGER)` veya EntityGraph |

## Best Practices

1. **Varsayılan olarak LAZY kullan** - EAGER performans sorunlarına yol açar
2. **N+1 problemini önle** - EntityGraph veya JOIN FETCH kullan
3. **DTO kullan** - Sadece ihtiyaç duyulan alanları çek
4. **Transaction sınırlarını bil** - Service metodu biter bitmez transaction kapanır
5. **Test et** - `spring.jpa.show-sql=true` ile sorguları izle

## Projemizdeki Örnek

**Sorun:** `TaskController.getAllActiveTasks()` → `TaskResponse` constructor'ında `task.getUser().getUsername()` çağrısı hata veriyor

**Neden:** 
1. Repository'de `@Query` ile sorgu yapılıyor
2. `User` proxy olarak geliyor  
3. `TaskResponse` constructor'ında User'a erişilmeye çalışılıyor
4. Transaction kapandığı için proxy DB'ye erişemiyor

**Çözüm:** Repository'e `@EntityGraph(value = "Task.withUser")` eklendi:
- Aynı sorgu içinde User JOIN yapılıyor
- Proxy yerine gerçek User objesi geliyor
- Transaction kapansa bile sorun olmuyor (veri zaten bellekte)
