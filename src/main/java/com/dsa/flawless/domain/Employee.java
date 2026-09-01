package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"username"})
)
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 50)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(length = 30, nullable = false)
    private String name;
    @Column(length = 200, nullable = false)
    private String address;
    @Column(length = 100, nullable = false)
    private String email;
    @Column(length = 20, nullable = false)
    private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Role role = Role.EMPLOYEE;
    @CreationTimestamp @Column(nullable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
