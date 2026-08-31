package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="employee")
@Getter
@Setter
public class Employee {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, unique = true, length = 50)
	private String username;
	
	@Column(nullable = false, length = 255)
	private String password;
	
	@Column(nullable = false, length = 30)
	private String name;
	
	@Column(nullable = false, length = 200)
	private String address;
	
	@Column(nullable = false, length = 100, unique = true)
	private String email;
	
	@Column(nullable = false, length = 20)
	private String phone;
	
	@Column(name = "department_id", nullable = false)
	private Long departmentId;
	
	@Column(name = "position_id", nullable = false)
	private Long positionId;
	
	@Column(nullable = false, length = 20) @Enumerated(EnumType.STRING)
	private Role role;
	
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
	
}
