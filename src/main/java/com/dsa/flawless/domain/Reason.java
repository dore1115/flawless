package com.dsa.flawless.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"attendance_id"})
)
public class Reason extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="attendance_id", nullable = false)
    private Attendance attendance;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 20)
    private ReasonType reasonType;
    @Column(name = "filename", nullable = false)
    private String filename;
    @Column(name = "uploaded_filename", nullable = false)
    private String uploadedFilename;
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

}
