-- =========================================================
-- ERP 개발 - MySQL 스키마 (docs/schema.sql)
-- 04_ERD_테이블정의서(테이블 설계서)를 기반으로 생성.
-- 요구사항 정의서(요건정의서) 및 성능_보안_결함_시딩_매트릭스 대조 리뷰 반영.
--
-- ⚠ 리뷰에서 발견되어 이 스크립트에 실제로 반영한 사항은 대화 답변을 참고하세요.
--    (stock.version 컬럼 제외, employee.email 추가, employee.role NOT NULL,
--     PK 컬럼명을 공통 설계 규칙대로 id로 통일, attendance.day_type 컬럼 분리로
--     ON_LEAVE를 clock_in_status에서 별도 분리 등)
-- =========================================================

CREATE DATABASE IF NOT EXISTS erp
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE erp;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------
-- 1. department (部署) - 부서 마스터
-- ---------------------------------------------------------
DROP TABLE IF EXISTS department;
CREATE TABLE department (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '부서 고유 식별자(기본키)',
    department_name VARCHAR(50)  NOT NULL                COMMENT '부서명. 신설/명칭수정 시 중복 불가',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '사용 가능 여부. 삭제 대신 0으로 전환(soft delete)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시(갱신 시 자동 반영)',
    PRIMARY KEY (id),
    KEY idx_department_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='부서 마스터';

-- ---------------------------------------------------------
-- 2. position (役職) - 직책 마스터
-- ---------------------------------------------------------
DROP TABLE IF EXISTS position;
CREATE TABLE position (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '직책 고유 식별자(기본키)',
    position_name VARCHAR(50) NOT NULL                COMMENT '직책명. 신설/명칭변경 시 중복 불가',
    is_active     TINYINT(1)  NOT NULL DEFAULT 1       COMMENT '사용 가능 여부. 삭제 대신 0으로 전환(soft delete)',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시(갱신 시 자동 반영)',
    PRIMARY KEY (id),
    KEY idx_position_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='직책 마스터';

-- ---------------------------------------------------------
-- 3. employee (社員) - 사원 계정
--    * email 컬럼 추가: REQ-F-S-004(Should)가 계정 생성 시 이름/이메일/초기비밀번호/권한을
--      필수 입력값으로 요구하고 "이메일/아이디 중복 시 에러 처리"까지 명시하고 있어 추가함.
--    * role은 원 설계서에 NN 표시가 없었으나, 인가 로직이 성립하려면 모든 계정에 권한이
--      있어야 하므로 NOT NULL + 기본값(EMPLOYEE)으로 변경.
--    * password: REQ-NF-L-001(비밀번호 평문 저장)이 아직 "미적용" 결함이므로, 이 컬럼은
--      해시 여부와 무관하게 VARCHAR(255)로 둠. 초기 구현 단계에서는 평문을 넣어 결함을
--      재현하고, 개선 단계에서 BCrypt 해시로 교체하면 됨(컬럼 정의 자체는 변경 불필요).
--    * department_id/position_id: 원래는 REQ-NF-H-008("코드 기준 조회, 삭제 시 소속 사원 조회"
--      인덱스 부재로 인한 Full scan) 결함을 재현하기 위해 의도적으로 인덱스를 걸지 않았으나,
--      2026-09-21 개선 완료 — idx_employee_department_id / idx_employee_position_id를 추가해
--      PositionRepository.findByPosition()/DepartmentRepository.findByDepartment()(직책·부서
--      삭제 시 "소속 사원 조회"에 쓰임)가 더 이상 employee 테이블을 Full scan하지 않도록 했다.
-- ---------------------------------------------------------
DROP TABLE IF EXISTS employee;
CREATE TABLE employee (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사원번호(기본키)',
    username      VARCHAR(50)  NOT NULL                COMMENT '로그인 아이디. 중복 불가',
    password      VARCHAR(255) NOT NULL                COMMENT '비밀번호. REQ-NF-L-001 개선 전까지는 평문 저장 상태를 재현',
    name          VARCHAR(30)  NOT NULL                COMMENT '이름',
    email         VARCHAR(100) NOT NULL                COMMENT '이메일. 계정 생성 시 필수, 중복 불가 (REQ-F-S-004)',
    address       VARCHAR(200)                         COMMENT '주소',
    phone         VARCHAR(20)  NOT NULL                COMMENT '휴대폰 번호',
    department_id BIGINT       NOT NULL                COMMENT 'department.id 참조',
    position_id   BIGINT       NOT NULL                COMMENT 'position.id 참조',
    role          VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE' COMMENT 'ADMIN_HR/ADMIN_IV/EMPLOYEE 중 하나',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at    DATETIME     NULL                    COMMENT '수정일시(공통 컬럼)',
    PRIMARY KEY (id),
    UNIQUE KEY uq_employee_username (username),
    UNIQUE KEY uq_employee_email (email),
    KEY idx_employee_department_id (department_id),
    KEY idx_employee_position_id (position_id),
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_employee_position   FOREIGN KEY (position_id)   REFERENCES position (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사원 계정';

-- ---------------------------------------------------------
-- 4. department_history (部署記録) - 부서 변경 이력 (append-only)
-- ---------------------------------------------------------
DROP TABLE IF EXISTS department_history;
CREATE TABLE department_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '기본키',
    department_id BIGINT       NOT NULL                COMMENT 'department.id 참조 (변경 대상 부서, soft delete되어도 참조 유지)',
    action        VARCHAR(20)  NOT NULL                COMMENT 'CREATE / UPDATE / DELETE',
    employee_id   BIGINT       NOT NULL                COMMENT 'employee.id 참조 (처리한 인사관리자)',
    before_value  VARCHAR(255)                         COMMENT '변경 전 department_name/is_active 값 (신설 시 NULL)',
    after_value   VARCHAR(255)                         COMMENT '변경 후 department_name/is_active 값 (삭제 시 NULL)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '처리일시. 최신순 정렬에 사용',
    updated_at    DATETIME     NULL                    COMMENT '공통 컬럼(이력은 불변 데이터이므로 값이 채워지지 않음)',
    PRIMARY KEY (id),
    CONSTRAINT fk_department_history_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_department_history_employee   FOREIGN KEY (employee_id)   REFERENCES employee (id),
    KEY idx_department_history_department_id (department_id),
    KEY idx_department_history_employee_id (employee_id),
    KEY idx_department_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='부서 변경 이력';

-- ---------------------------------------------------------
-- 5. position_history (役職記録) - 직책 변경 이력 (append-only)
-- ---------------------------------------------------------
DROP TABLE IF EXISTS position_history;
CREATE TABLE position_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '기본키',
    position_id  BIGINT       NOT NULL                COMMENT 'position.id 참조 (변경 대상 직책, soft delete되어도 참조 유지)',
    action       VARCHAR(20)  NOT NULL                COMMENT 'CREATE / UPDATE / DELETE',
    employee_id  BIGINT       NOT NULL                COMMENT 'employee.id 참조 (처리한 인사관리자)',
    before_value VARCHAR(255)                         COMMENT '변경 전 position_name/is_active 값 (신설 시 NULL)',
    after_value  VARCHAR(255)                         COMMENT '변경 후 position_name/is_active 값 (삭제 시 NULL)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '처리일시. 최신순 정렬에 사용',
    updated_at   DATETIME     NULL                    COMMENT '공통 컬럼(이력은 불변 데이터이므로 값이 채워지지 않음)',
    PRIMARY KEY (id),
    CONSTRAINT fk_position_history_position FOREIGN KEY (position_id) REFERENCES position (id),
    CONSTRAINT fk_position_history_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    KEY idx_position_history_position_id (position_id),
    KEY idx_position_history_employee_id (employee_id),
    KEY idx_position_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='직책 변경 이력';

-- ---------------------------------------------------------
-- 6. employee_history (社員配置履歴) - 사원 배치(직책/부서) 변경 이력
-- ---------------------------------------------------------
DROP TABLE IF EXISTS employee_history;
CREATE TABLE employee_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '기본키',
    employee_id   BIGINT       NOT NULL                COMMENT 'employee.id 참조 (변경 대상 사원)',
    changed_field VARCHAR(30)  NOT NULL                COMMENT 'POSITION / DEPARTMENT 등 변경된 필드 구분',
    changed_by    BIGINT       NOT NULL                COMMENT 'employee.id 참조 (처리한 인사관리자). 대상 사원과 같은 테이블을 참조하지만 역할이 달라 컬럼명을 구분(의도적 예외)',
    before_value  VARCHAR(255)                         COMMENT '변경 전 position_id/department_id 값',
    after_value   VARCHAR(255)                         COMMENT '변경 후 position_id/department_id 값',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '처리일시. 최신순 정렬에 사용',
    updated_at    DATETIME     NULL                    COMMENT '공통 컬럼(이력은 불변 데이터이므로 값이 채워지지 않음)',
    PRIMARY KEY (id),
    CONSTRAINT fk_employee_history_target  FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_employee_history_actor   FOREIGN KEY (changed_by)  REFERENCES employee (id),
    KEY idx_employee_history_employee_id (employee_id),
    KEY idx_employee_history_changed_by (changed_by),
    KEY idx_employee_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사원 배치 변경 이력';

-- ---------------------------------------------------------
-- 7. item (品目) - 품목 마스터
-- ---------------------------------------------------------
DROP TABLE IF EXISTS item;
CREATE TABLE item (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '품목번호(기본키)',
    name       VARCHAR(100) NOT NULL                COMMENT '품목명. 중복 등록 불가',
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '논리 삭제(비활성화) 플래그. TRUE인 품목은 입출고 대상에서 제외',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at DATETIME     NULL                    COMMENT '공통 컬럼',
    PRIMARY KEY (id),
    UNIQUE KEY uq_item_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='품목 마스터';

-- ---------------------------------------------------------
-- 8. stock (在庫) - 품목별 현재 재고 수량
--    * 원 설계서의 version(낙관적 락) 컬럼은 이 스크립트에서 제외했습니다.
--      REQ-NF-I-005(재고 모듈 동시성 결함)가 아직 "미적용" 상태인 의도적 결함이라,
--      처음부터 버전 컬럼이 있으면 결함을 재현/측정할 수 없습니다.
--      해당 결함을 개선할 때 `ALTER TABLE stock ADD COLUMN version INT NOT NULL DEFAULT 0;`
--      로 추가하고 JPA @Version을 적용하세요.
-- ---------------------------------------------------------
DROP TABLE IF EXISTS stock;
CREATE TABLE stock (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '재고번호(기본키)',
    item_id    BIGINT   NOT NULL                COMMENT 'item.id 참조. 품목 1개당 재고 행 최대 1개',
    quantity   INT      NOT NULL DEFAULT 0       COMMENT '현재수량. 0 이상. 입고 시 증가, 출고 시 감소',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시(해당 품목 최초 입고 시 생성)',
    updated_at DATETIME NULL                     COMMENT '공통 컬럼',
    PRIMARY KEY (id),
    UNIQUE KEY uq_stock_item_id (item_id),
    CONSTRAINT fk_stock_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT chk_stock_quantity CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='품목별 현재 재고';

-- ---------------------------------------------------------
-- 9. stock_history (入出庫履歴) - 입출고 이력 (append-only)
-- ---------------------------------------------------------
DROP TABLE IF EXISTS stock_history;
CREATE TABLE stock_history (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '이력번호(기본키)',
    item_id     BIGINT      NOT NULL                COMMENT 'item.id 참조',
    move_type   VARCHAR(20) NOT NULL                COMMENT 'IN(입고) / OUT(출고)',
    quantity    INT         NOT NULL                COMMENT '처리수량. 1 이상',
    employee_id BIGINT      NOT NULL                COMMENT 'employee.id 참조(처리한 물류관리자)',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '처리(입고/출고) 발생 시각. 최신순 정렬에 사용',
    updated_at  DATETIME    NULL                    COMMENT '공통 컬럼(이력은 불변 데이터이므로 값이 채워지지 않음)',
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_history_item     FOREIGN KEY (item_id)     REFERENCES item (id),
    CONSTRAINT fk_stock_history_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT chk_stock_history_quantity CHECK (quantity >= 1),
    KEY idx_stock_history_item_id (item_id),
    KEY idx_stock_history_employee_id (employee_id),
    KEY idx_stock_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='입출고 이력';

-- ---------------------------------------------------------
-- 10. attendance (勤怠) - 일별 출퇴근 기록
--     출근 시 1건 생성, 퇴근 처리 시 같은 행을 갱신. 확정(퇴근 처리/결근 배치) 이후 갱신하지 않음.
--     휴가 승인 시에는 clock_in 없이 day_type=LEAVE로 직접 생성함(leave_balance 갱신과 같은
--     트랜잭션에서 처리).
--     * day_type 컬럼: "그 날이 정상 근무 예정일이었는가"를 나타내는 값으로,
--       clock_in_status/clock_out_status(실제 펀치 결과)와 개념을 분리했습니다.
--       기존에는 ON_LEAVE를 clock_in_status 안에 넣었으나, ON_TIME/LATE/ABSENT는
--       "언제 왔는가"를 답하는 값인 반면 휴가는 "애초에 출근 예정이 없었는가"를 답하는
--       값이라 성격이 달라 별도 컬럼으로 분리했습니다. 이렇게 하면 무단결근(day_type=NORMAL
--       이면서 clock_in_status=ABSENT)과 승인된 휴가(day_type=LEAVE)가 근태율/급여 계산
--       쿼리에서 절대 섞이지 않습니다.
-- ---------------------------------------------------------
DROP TABLE IF EXISTS attendance;
CREATE TABLE attendance (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '근태번호(기본키)',
    employee_id         BIGINT       NOT NULL                COMMENT 'employee.id 참조',
    work_date           DATE         NOT NULL                COMMENT '근무일자. 결근·휴가로 처리된 날도 이 값으로 식별',
    day_type            VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '근태 유형. NORMAL(정상 근무일)/LEAVE(휴가) 중 하나. day_type=LEAVE인 날은 clock_in/clock_in_status가 모두 NULL',
    clock_in            DATETIME                              COMMENT '출근시각. 결근·휴가인 날은 NULL',
    clock_in_status     VARCHAR(20)                           COMMENT 'ON_TIME/LATE/ABSENT 중 하나. day_type=LEAVE인 경우 NULL',
    clock_out           DATETIME                              COMMENT '퇴근시각. 미퇴근·결근·휴가인 경우 NULL',
    clock_out_status    VARCHAR(20)                           COMMENT 'ON_TIME/EARLY 중 하나. 출근이 없거나 퇴근 전이면 NULL',
    late_minutes        INT          NOT NULL DEFAULT 0       COMMENT '09:00 대비 지각 분(REQ-F-S-003 연간 누적에 사용)',
    early_leave_minutes INT          NOT NULL DEFAULT 0       COMMENT '18:00 대비 조퇴 분(REQ-F-S-003 연간 누적에 사용)',
    overtime_minutes    INT          NOT NULL DEFAULT 0       COMMENT '18:00 초과 근무 분(잔업시간)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '출근 기록 생성 시각',
    updated_at          DATETIME     NULL                     COMMENT '퇴근 처리/결근 배치 확정 시 갱신, 확정 후 갱신하지 않음',
    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_employee_date (employee_id, work_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일별 근태 기록';

-- ---------------------------------------------------------
-- 11. reason (事由書) - 지각/조퇴/결근 소명 사유서
-- ---------------------------------------------------------
DROP TABLE IF EXISTS reason;
CREATE TABLE reason (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사유서번호(기본키)',
    attendance_id      BIGINT       NOT NULL                COMMENT 'attendance.id 참조. 근태 1건당 사유서 1건만 허용',
    employee_id        BIGINT       NOT NULL                COMMENT 'employee.id 참조 (attendance_id로 알 수 있으나 목록 조회 편의를 위한 의도적 비정규화)',
    reason_type        VARCHAR(20)  NOT NULL                COMMENT 'ANNUAL(연차)/OFFICIAL(공가)/SICK(병가)/UNEXCUSED(무단) 중 하나',
    filename           VARCHAR(255) NOT NULL                COMMENT '서버에 저장된 실제 파일명(UUID 등)',
    uploaded_filename  VARCHAR(255) NOT NULL                COMMENT '사용자가 업로드 시 사용한 원본 파일명',
    submitted_at       DATETIME     NOT NULL                COMMENT '제출일시. 30일 이내 소명 판정(REQ-F-C-001, REQ-NF-A-004)에 사용',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at         DATETIME     NULL                    COMMENT '공통 컬럼',
    PRIMARY KEY (id),
    UNIQUE KEY uq_reason_attendance_id (attendance_id),
    CONSTRAINT fk_reason_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (id),
    CONSTRAINT fk_reason_employee   FOREIGN KEY (employee_id)   REFERENCES employee (id),
    KEY idx_reason_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근태 소명 사유서';

-- ---------------------------------------------------------
-- 12. reason_status (事由書状態) - 사유서 승인/반려 상태
-- ---------------------------------------------------------
DROP TABLE IF EXISTS reason_status;
CREATE TABLE reason_status (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '기본키',
    reason_id       BIGINT       NOT NULL                COMMENT 'reason.id 참조. 사유서 1건당 상태 1건만 허용',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING(대기)/APPROVED(승인)/REJECTED(반려) 중 하나',
    is_editable     BOOLEAN      NOT NULL DEFAULT TRUE    COMMENT '승인 전 TRUE(수정 가능), 승인 시 FALSE, 반려 시 다시 TRUE',
    rejected_reason VARCHAR(100)                          COMMENT 'status=REJECTED일 때만 값 존재',
    processed_by    BIGINT                                COMMENT 'employee.id 참조(승인·반려한 인사관리자). 대기 상태에서는 NULL',
    processed_at    DATETIME                              COMMENT '승인·반려 처리 시각. 대기 상태에서는 NULL',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at      DATETIME     NULL                     COMMENT '공통 컬럼',
    PRIMARY KEY (id),
    UNIQUE KEY uq_reason_status_reason_id (reason_id),
    CONSTRAINT fk_reason_status_reason   FOREIGN KEY (reason_id)    REFERENCES reason (id),
    CONSTRAINT fk_reason_status_employee FOREIGN KEY (processed_by) REFERENCES employee (id),
    KEY idx_reason_status_processed_by (processed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사유서 처리 상태';

-- ---------------------------------------------------------
-- 13. leave_balance (休暇残日数) - 연도별 휴가 사용 현황
-- ---------------------------------------------------------
DROP TABLE IF EXISTS leave_balance;
CREATE TABLE leave_balance (
    id                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '기본키',
    employee_id          BIGINT   NOT NULL                COMMENT 'employee.id 참조',
    year                 SMALLINT NOT NULL                COMMENT '대상연도(예: 2026)',
    annual_leave_total   INT      NOT NULL DEFAULT 20      COMMENT '해당 연도 연차 부여일수',
    annual_leave_used    INT      NOT NULL DEFAULT 0       COMMENT '연차 사용일수(잔여 = total - used, 계산으로 구함)',
    sick_leave_total     INT      NOT NULL DEFAULT 10      COMMENT '해당 연도 병가 부여일수',
    sick_leave_used      INT      NOT NULL DEFAULT 0       COMMENT '병가 사용일수(잔여 = total - used, 계산으로 구함)',
    official_leave_used  INT      NOT NULL DEFAULT 0       COMMENT '공가 사용일수(총일수 제한 없음)',
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at           DATETIME NULL                     COMMENT '공통 컬럼',
    PRIMARY KEY (id),
    UNIQUE KEY uq_leave_balance_employee_year (employee_id, year),
    CONSTRAINT fk_leave_balance_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT chk_leave_balance_annual_used CHECK (annual_leave_used >= 0 AND annual_leave_used <= annual_leave_total),
    CONSTRAINT chk_leave_balance_sick_used   CHECK (sick_leave_used >= 0 AND sick_leave_used <= sick_leave_total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='연도별 휴가 사용 현황';

SET FOREIGN_KEY_CHECKS = 1;
