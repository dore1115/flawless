package com.dsa.flawless.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"attendance_id"})
)
public class Reason {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name="attendance_id")
    private Attendance attendance;
}
