package com.dsa.flawless.controller;

import com.dsa.flawless.domain.Employee;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {
	
	@GetMapping("/login")
	public String joinForm(Model model){
		model.addAttribute("employee", new Employee());
		return "auth/login";
	}
}
