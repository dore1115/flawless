package com.dsa.flawless.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * AU-002(사원 계정 등록, POST /admin/members)의 폼 바인딩 객체.
 * Employee는 Builder 전용(setter 없음)이라 자동 바인딩 대상이 될 수 없어, 별도 DTO로 값을 받는다.
 */
@Getter
@Setter
public class EmployeeRegisterForm {
    private String username;
    private String password;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Long departmentId;
    private Long positionId;
    private String role;
}
