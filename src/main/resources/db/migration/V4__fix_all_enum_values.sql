-- V4: Tum status ve priority degerlerini enum ile uyumlu hale getir
-- Bozuk, yanlis yazilmis, buyuk/kucuk harf hatali tum degerleri duzeltir.
-- Gecerli enum degerleri:
--   TaskStatus:   PENDING, IN_PROGRESS, COMPLETED, CANCELLED
--   TaskPriority: LOW, MEDIUM, HIGH

-- =============================================
-- TASK STATUS DUZELTMELERI
-- =============================================

-- Eski/alternatif degerler -> PENDING
UPDATE tasks SET status = 'PENDING'
WHERE UPPER(TRIM(status)) IN ('TODO', 'NEW', 'OPEN', 'CREATED', 'PENDING')
  AND status != 'PENDING';

-- Eski/alternatif degerler -> IN_PROGRESS
UPDATE tasks SET status = 'IN_PROGRESS'
WHERE UPPER(TRIM(status)) IN ('IN_PROGRESS', 'INPROGRESS', 'IN-PROGRESS', 'ACTIVE', 'STARTED', 'DOING', 'WIP')
  AND status != 'IN_PROGRESS';

-- Eski/alternatif degerler -> COMPLETED
UPDATE tasks SET status = 'COMPLETED'
WHERE UPPER(TRIM(status)) IN ('COMPLETED', 'DONE', 'FINISHED', 'CLOSED', 'RESOLVED')
  AND status != 'COMPLETED';

-- Eski/alternatif degerler -> CANCELLED
UPDATE tasks SET status = 'CANCELLED'
WHERE UPPER(TRIM(status)) IN ('CANCELLED', 'CANCELED', 'CANCEL', 'DELETED', 'REMOVED', 'REJECTED')
  AND status != 'CANCELLED';

-- Kalan tanimlanamayan status degerleri -> PENDING (guvenli varsayilan)
UPDATE tasks SET status = 'PENDING'
WHERE status NOT IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

-- =============================================
-- TASK PRIORITY DUZELTMELERI
-- =============================================

-- Eski/alternatif degerler -> LOW
UPDATE tasks SET priority = 'LOW'
WHERE UPPER(TRIM(priority)) IN ('LOW', 'LO', 'LOWEST', 'MINOR', 'L')
  AND priority != 'LOW';

-- Eski/alternatif degerler -> MEDIUM
UPDATE tasks SET priority = 'MEDIUM'
WHERE UPPER(TRIM(priority)) IN ('MEDIUM', 'MEDIM', 'MED', 'MEDUM', 'MEDIUN', 'MID', 'NORMAL', 'MIDDLE', 'M', 'DEFAULT')
  AND priority != 'MEDIUM';

-- Eski/alternatif degerler -> HIGH
UPDATE tasks SET priority = 'HIGH'
WHERE UPPER(TRIM(priority)) IN ('HIGH', 'HIHH', 'HIH', 'HIGHT', 'HEIGH', 'HI', 'CRITICAL', 'URGENT', 'HIGHEST', 'MAJOR', 'H')
  AND priority != 'HIGH';

-- Kalan tanimlanamayan priority degerleri -> MEDIUM (guvenli varsayilan)
UPDATE tasks SET priority = 'MEDIUM'
WHERE priority NOT IN ('LOW', 'MEDIUM', 'HIGH');
