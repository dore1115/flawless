package com.dsa.flawless.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * REQ-NF-G-004 개선: 인사정보 / 근태 / 재고관리 서비스의 조회·변경 감사 로그.
 *
 * 대상 서비스의 public 메서드가 호출될 때마다 "누가, 어떤 업무를, 어떤 값으로, 결과가 어땠는지"를
 * 한 줄로 남긴다. 각 서비스 메서드에 로그 코드를 흩어 넣지 않고 이 Aspect 한 곳에서 처리하므로,
 * 대상 서비스에 메서드가 추가돼도 자동으로 로그가 남는다.
 *
 * 로그 형식(로거 이름: AUDIT → logging.level.AUDIT 로 레벨만 따로 조절 가능):
 *   [AUDIT][재고][변경] user=kim ItemService.create(name=볼펜) - 성공 (12ms)
 *   [AUDIT][재고][변경] user=kim ItemService.create(name=볼펜) - 실패: DuplicateItemNameException (3ms)
 *
 * - 조회/변경 구분: @Transactional(readOnly = true)면 조회, 그 외는 변경.
 * - 비밀번호 등 민감한 파라미터는 값 대신 **** 로 남긴다.
 * - Entity가 인자로 넘어와도 toString()으로 지연 로딩이 일어나지 않도록, 단순 값/DTO만 내용을 남기고
 *   그 외 객체는 타입 이름만 남긴다. 반환값은 남기지 않는다(조회 결과에 개인정보가 포함될 수 있음).
 * - 예외의 스택트레이스는 GlobalExceptionHandler가 남기므로 여기서는 예외 타입만 남긴다.
 */
@Slf4j(topic = "AUDIT")
@Aspect
@Component
public class AuditLogAspect {

    private static final Pattern SENSITIVE_PARAM = Pattern.compile("(?i).*(password|passwd|pwd|secret|token).*");
    private static final String DTO_PACKAGE = "com.dsa.flawless.dto.";
    private static final int MAX_VALUE_LENGTH = 100;

    @Pointcut("within(com.dsa.flawless.service.EmployeeService)"
            + " || within(com.dsa.flawless.service.EmployeeRegisterService)"
            + " || within(com.dsa.flawless.service.EmployeeAssignmentService)"
            + " || within(com.dsa.flawless.service.DepartmentService)"
            + " || within(com.dsa.flawless.service.PositionService)")
    private void hrService() {}

    @Pointcut("within(com.dsa.flawless.service.AttendanceService)")
    private void attendanceService() {}

    @Pointcut("within(com.dsa.flawless.service.InventoryService)"
            + " || within(com.dsa.flawless.service.ItemService)")
    private void inventoryService() {}

    @Around("execution(public * *(..)) && hrService()")
    public Object auditHr(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, "인사");
    }

    @Around("execution(public * *(..)) && attendanceService()")
    public Object auditAttendance(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, "근태");
    }

    @Around("execution(public * *(..)) && inventoryService()")
    public Object auditInventory(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, "재고");
    }

    private Object audit(ProceedingJoinPoint joinPoint, String domain) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String action = isReadOnly(method, joinPoint.getTarget().getClass()) ? "조회" : "변경";
        String call = signature.getDeclaringType().getSimpleName() + "." + method.getName()
                + "(" + formatArgs(signature.getParameterNames(), joinPoint.getArgs()) + ")";
        String user = currentUser();

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("[AUDIT][{}][{}] user={} {} - 성공 ({}ms)",
                    domain, action, user, call, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            log.warn("[AUDIT][{}][{}] user={} {} - 실패: {} ({}ms)",
                    domain, action, user, call, e.getClass().getSimpleName(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    private boolean isReadOnly(Method method, Class<?> targetClass) {
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        if (tx == null) {
            tx = AnnotatedElementUtils.findMergedAnnotation(targetClass, Transactional.class);
        }
        return tx != null && tx.readOnly();
    }

    private String currentUser() {
        // HTTP 요청 밖(스케줄러 등)에서 호출된 경우
        if (RequestContextHolder.getRequestAttributes() == null) {
            return "system";
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String formatArgs(String[] names, Object[] args) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < args.length; i++) {
            String name = names != null && i < names.length ? names[i] : "arg" + i;
            String value = SENSITIVE_PARAM.matcher(name).matches() ? "****" : formatValue(args[i]);
            joiner.add(name + "=" + value);
        }
        return joiner.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        boolean printable = value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof Temporal || value instanceof Pageable
                || value.getClass().getName().startsWith(DTO_PACKAGE);
        if (!printable) {
            return value.getClass().getSimpleName();
        }
        String text = String.valueOf(value);
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "..." : text;
    }
}
