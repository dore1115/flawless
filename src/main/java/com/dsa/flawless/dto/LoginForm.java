package com.dsa.flawless.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginForm {
	
	@NotBlank(message = "아이디를 입력해 주세요.")
	@Size(min = 4, max = 30, message = "아이디는 4~30자로 입력해 주세요.")
	private String username;
	
	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, message = "비밀번호는 8자 이상 입력해 주세요.")
	private String password;

}