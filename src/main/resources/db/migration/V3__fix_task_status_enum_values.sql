-- V3: Eski status degerlerini yeni enum degerleriyle eslestir
-- Veritabanindaki 'TODO' degeri entity'deki TaskStatus enum'unda yok.
-- TODO -> PENDING olarak donusturuluyor. Veri silinmez, donusturulur.

UPDATE tasks SET status = 'PENDING' WHERE status = 'TODO';
