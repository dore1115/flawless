package com.dsa.flawless.exception;

/**
 * REQ-F-S-009(품목 등록)·REQ-F-S-010(품목 정보 수정) 비고란: "동일한 품목명으로 중복 등록/변경은
 * 허용하지 않음"을 위반했을 때(이미 존재하는 품목명으로 등록/수정을 시도했을 때) 던지는 예외.
 *
 * ItemNotFoundException과 마찬가지로 이 프로젝트에는 아직 GlobalExceptionHandler가 없어서
 * (REQ-NF-G-004, 의도적으로 비워둔 결함) ItemController가 이 예외를 직접 잡아, 폼 화면(모달)에
 * 필드 오류로 표시.
 */
public class DuplicateItemNameException extends RuntimeException {
    public DuplicateItemNameException(String message) {
        super(message);
    }
}
