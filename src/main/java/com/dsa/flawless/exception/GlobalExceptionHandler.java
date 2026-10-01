package com.dsa.flawless.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dsa.flawless.util.LogMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;

/**
 * REQ-NF-G-004 개선: 전역 예외 처리 표준.
 *
 * 컨트롤러/서비스/도메인에서 잡히지 않은 예외를 한 곳에서 처리한다.
 * - 서버: 예외 내용(500 계열은 스택트레이스 포함)을 로그로 남긴다.
 * - 사용자: 스택트레이스나 내부 예외 메시지 대신 templates/error.html 화면을 본다.
 *
 * BusinessException을 상속한 예외(ItemNotFoundException, DuplicateItemNameException 등)도 여기서 처리한다.
 * 컨트롤러가 직접 잡아서 폼 화면에 오류를 표시하는 예외(InsufficientStockException 등)는
 * 기존처럼 컨트롤러 로컬 처리가 우선하고, 여기까지 올라오지 않는다.
 * DispatcherServlet 밖(필터 등)에서 발생한 예외는 Spring Boot의 BasicErrorController가
 * 같은 error.html 템플릿으로 처리한다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_VIEW = "error";
    private static final String DEFAULT_MESSAGE = "요청을 처리하는 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    // 비즈니스 규칙 위반: ErrorCode에 정의된 상태코드/메시지를 그대로 사용자에게 보여준다.
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[{}] {} {} - code={}, detail={}", e.getClass().getSimpleName(),
                request.getMethod(), request.getRequestURI(), errorCode.name(), LogMasker.maskText(e.getMessage()));
        return errorView(errorCode.getStatus(), errorCode.getMessage(), request, response);
    }

    // @PreAuthorize 등 메서드 보안에서 거부된 경우
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDenied(AccessDeniedException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[AccessDenied] {} {} - user={}",
                request.getMethod(), request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous");
        return errorView(HttpStatus.FORBIDDEN, "해당 페이지에 접근할 권한이 없습니다.", request, response);
    }

    // 그 외 모든 예외
    // - Spring MVC 표준 예외(검증 실패, 파라미터 누락/타입 불일치, 지원하지 않는 메서드, 없는 경로,
    //   ResponseStatusException 등)는 ErrorResponse를 구현하므로 그 상태코드를 그대로 사용한다.
    // - 예상하지 못한 예외는 스택트레이스를 서버 로그에만 남기고, 사용자에겐 일반 메시지만 보여준다.
    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        HttpStatusCode status = e instanceof ErrorResponse errorResponse
                ? errorResponse.getStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR;

        if (status.is5xxServerError()) {
            log.error("[{}] {} {}", e.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(), e);
            return errorView(status, DEFAULT_MESSAGE, request, response);
        }
        // 검증 실패 메시지 등에는 사용자가 입력한 값(rejected value)이 그대로 들어 있을 수 있어 마스킹해서 남긴다.
        log.warn("[{}] {} {} - {}", e.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(),
                LogMasker.maskText(e.getMessage()));
        return errorView(status, clientErrorMessage(status), request, response);
    }

    private String clientErrorMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "요청하신 페이지를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 요청 방식입니다.";
            default -> "잘못된 요청입니다. 입력값을 확인해 주세요.";
        };
    }

    // BasicErrorController가 error.html에 넘기는 모델 속성 이름(status, error, message, path, timestamp)과 맞춘다.
    private ModelAndView errorView(HttpStatusCode status, String message, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(status.value());
        HttpStatus resolved = HttpStatus.resolve(status.value());

        ModelAndView mav = new ModelAndView(ERROR_VIEW);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", resolved != null ? resolved.getReasonPhrase() : "Error");
        mav.addObject("message", message);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("timestamp", LocalDateTime.now());
        return mav;
    }
}
