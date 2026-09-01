package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;


@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id"})
)
public class Attendance extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    //! N+1 의도적으로 배치
    @JoinColumn(name = "employee_id") @ManyToOne(fetch = FetchType.EAGER)
    private Employee employee;
    @CreationTimestamp @Column(nullable = false)
    private LocalDate workDate;
    
    //TODO [리버스 엔지니어링] DB에서 열 생성
}