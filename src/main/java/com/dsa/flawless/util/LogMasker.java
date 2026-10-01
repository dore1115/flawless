package com.dsa.flawless.util;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 로그에 남기는 값의 개인정보/비밀번호 마스킹 유틸.
 *
 * - 값의 종류를 알 때(파라미터 이름 등): maskPassword / maskName / maskEmail / maskPhone / maskAddress
 * - 종류를 모르는 자유 텍스트(예외 메시지, DTO toString 등): maskText 로 이메일·전화번호·주민등록번호 패턴을 찾아 가린다.
 *
 * 예) 홍길동 → 홍*동, hong.gildong@corp.com → ho***@corp.com, 010-1234-5678 → 010-****-5678,
 *     서울특별시 강남구 테헤란로 123 → 서울특별시 강남구 ***, 비밀번호 → ****
 */
public final class LogMasker {

    private static final String MASK = "****";

    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+-]{1,2})[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(0\\d{1,2})[-.\\s]?\\d{3,4}[-.\\s]?(\\d{4})(?!\\d)");
    private static final Pattern RRN = Pattern.compile(
            "(?<!\\d)(\\d{6})[-\\s]?[1-4]\\d{6}(?!\\d)");

    private LogMasker() {
    }

    public static String maskPassword(Object value) {
        return value == null ? "null" : MASK;
    }

    // 홍길동 → 홍*동, 김철 → 김*, 남궁민수 → 남**수
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String trimmed = name.trim();
        int length = trimmed.length();
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "*".repeat(length - 2) + trimmed.charAt(length - 1);
    }

    // hong.gildong@corp.com → ho***@corp.com
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    // 010-1234-5678 / 01012345678 → 010-****-5678
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return MASK;
        }
        String prefix = digits.startsWith("02") ? "02" : digits.substring(0, 3);
        return prefix + "-****-" + digits.substring(digits.length() - 4);
    }

    // 서울특별시 강남구 테헤란로 123 → 서울특별시 강남구 ***  (시/구 단위까지만 남김)
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length <= 2) {
            return tokens[0] + " ***";
        }
        return String.join(" ", Arrays.copyOf(tokens, 2)) + " ***";
    }

    // 종류를 모르는 텍스트에서 이메일·전화번호·주민등록번호 패턴을 찾아 가린다.
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = RRN.matcher(text).replaceAll("$1-*******");
        masked = EMAIL.matcher(masked).replaceAll("$1***@$2");
        masked = PHONE.matcher(masked).replaceAll("$1-****-$2");
        return masked;
    }
}
