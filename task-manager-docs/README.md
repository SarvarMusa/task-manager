# Task Manager Dokümantasyonu

Bu dizin, Task Manager projesinde yapılan tüm değişikliklerin, iyileştirmelerin ve özelliklerin detaylı dokümantasyonunu içerir.

## Dokümantasyon Formatı

Her değişiklik/özellik için aşağıdaki format kullanılır:

1. **Sorun (Problem)** - Neden bu değişikliğe ihtiyaç duyduk?
2. **Çözüm (Solution)** - Nasıl çözdük?
3. **Neler Kazandık** - Bu değişiklikten ne fayda sağladık?
4. **Avantajlar** - Detaylı avantaj listesi
5. **Dezavantajlar** - Potansiyel dezavantajlar ve çözümleri
6. **Etkilenen Dosyalar** - Hangi dosyalar değişti?

## Doküman Listesi

| # | Başlık | Tarih | Durum |
|---|--------|-------|-------|
| 01 | [Enum Kullanımı - TaskPriority ve TaskStatus](01-enum-implementation.md) | 2026-02-26 | ✅ Tamamlandı |
| 02 | [Pagination (Sayfalama) Implementasyonu](02-pagination-implementation.md) | 2026-02-26 | ✅ Tamamlandı |
| 03 | [Flyway Database Migration Implementasyonu](03-flyway-migration.md) | 2026-02-26 | ✅ Tamamlandı |

## Yeni Değişiklik Ekleme

Yeni bir değişiklik yaparken:

1. Sonraki sıra numarasını kullan (02-, 03-, vs.)
2. Tarih ekleyin
3. Sorun → Çözüm → Kazanımlar → Avantajlar → Dezavantajlar formatını takip edin
4. Etkilenen tüm dosyaları listeleyin
5. Örnek kod ve API kullanımı ekleyin

## Notlar

- Her doküman bağımsız okunabilir olmalı
- Karar verme sürecini açıklayın (neden bu yaklaşım?)
- Alternatif çözümleri de değerlendirin
- Pratik örnekler ekleyin
- Dezavantajları saklamayın, karşılaştırın

---

**Proje:** Task Manager API  
**Tech Stack:** Spring Boot 3.2, Java 17, PostgreSQL/H2, Flyway  
**Son Güncelleme:** 2026-02-26
