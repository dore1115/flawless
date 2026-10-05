-- ─────────────────────────────────────────────────────────────
-- 부하테스트용 데이터 증량 (seed_data.sql 적용 후 1회 실행)
--   docker exec -i erp-mysql mysql -uroot -p$DB_ROOT_PASSWORD erp < container/k6/nfr/data/z-scale-up.sql
--
-- 개선 전/후 비교는 "같은 데이터"에서 해야 하므로, 이 스크립트를 적용한 DB 볼륨 상태를 고정해두고
-- seed 브랜치 / fix 브랜치 앱 이미지만 바꿔서 측정할 것.
-- ─────────────────────────────────────────────────────────────
SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 200000;

-- [1] REQ-NF-I-003/004: 품목 5,000건 추가 (+ 재고 행) → 총 5,050건, MAX_PAGE=504
INSERT INTO item (name, is_deleted)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5000)
SELECT CONCAT('부하테스트 품목 ', LPAD(n, 5, '0')), 0 FROM seq;

INSERT INTO stock (item_id, quantity)
SELECT i.id, 100 FROM item i LEFT JOIN stock s ON s.item_id = i.id
WHERE s.id IS NULL AND i.name LIKE '부하테스트 품목 %';

-- [2] REQ-NF-A-003/004: 근태를 과거 4년치 더 복제 (1인당 ≈260행 → ≈1,300행)
--     개선 전(전체 기간 조회)은 행 수에 비례해 느려지고, 개선 후(기간 조건)는 거의 그대로여야 한다.
SET @s := (SELECT MIN(work_date) FROM attendance);
INSERT IGNORE INTO attendance (employee_id, work_date, day_type, clock_in, clock_in_status, clock_out, clock_out_status,
                               late_minutes, early_leave_minutes, overtime_minutes, created_at, updated_at)
SELECT a.employee_id,
       DATE_SUB(a.work_date, INTERVAL k.y YEAR), a.day_type,
       DATE_SUB(a.clock_in,  INTERVAL k.y YEAR), a.clock_in_status,
       DATE_SUB(a.clock_out, INTERVAL k.y YEAR), a.clock_out_status,
       a.late_minutes, a.early_leave_minutes, a.overtime_minutes,
       DATE_SUB(a.created_at, INTERVAL k.y YEAR), DATE_SUB(a.updated_at, INTERVAL k.y YEAR)
FROM attendance a
JOIN (SELECT 1 y UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) k
WHERE a.work_date < DATE_ADD(@s, INTERVAL 1 YEAR);

-- [3] (선택) REQ-NF-H-008: 사원 50,000명 추가 — 인덱스 유무 차이가 보이려면 행 수가 커야 함
--     비밀번호는 시드와 동일한 BCrypt 해시(평문 password123!). 필요할 때만 주석 해제.
-- INSERT INTO employee (username, password, name, email, address, phone, department_id, position_id, role)
-- WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000)
-- SELECT CONCAT('load', LPAD(n, 6, '0')), '$2a$10$Bx7oE4nj.iFBYhYtxvaOMOyOSAbJatllleTJjEjZwfuguQfCybayS', CONCAT('부하', n), CONCAT('load', n, '@k6.test'),
--        '부산광역시', '010-0000-0000', 1 + (n % 6), 1 + (n % 6), 'EMPLOYEE'
-- FROM seq;

-- 확인
SELECT (SELECT COUNT(*) FROM item) AS items, (SELECT COUNT(*) FROM stock) AS stocks,
       (SELECT COUNT(*) FROM attendance) AS attendances, (SELECT COUNT(*) FROM employee) AS employees;
