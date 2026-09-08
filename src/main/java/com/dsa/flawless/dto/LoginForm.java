package com.dsa.flawless.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginForm {
	// TODO(REQ-NF-S-001 정책값 하드코딩): min=4,max=30이 어노테이션에 박혀 있어
	// 값을 바꾸려면 코드 수정 + 재배포가 필요하다. Bean Validation 어노테이션은
	// application.yml 값을 직접 못 받으므로, @ConfigurationProperties로 정책값을
	// 읽어와 서비스/커스텀 Validator 단에서 검증하는 방식으로 분리 검토.
	@NotBlank(message = "아이디를 입력해 주세요.")
	@Size(min = 4, max = 30, message = "아이디는 4~30자로 입력해 주세요.")
	private String username;
	
	// TODO(REQ-NF-S-001 정책값 하드코딩): 비밀번호 최소 길이(8)도 동일하게 하드코딩.
	// application.yml의 app.security.password.min-length 같은 설정값으로 분리 검토.
	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, message = "비밀번호는 8자 이상 입력해 주세요.")
	private String password;

}