-- V2: Tasks tablosuna due_date kolonu ekleme
-- Entity'de yeni bir alan eklendiginde bu sekilde migration yazilir.
-- Mevcut veriler korunur, yeni kolon NULL olarak eklenir.

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS due_date TIMESTAMP;

-- Ornek: Mevcut tasklara varsayilan due_date atama (opsiyonel)
-- UPDATE tasks SET due_date = created_at + INTERVAL '7 days' WHERE due_date IS NULL;
