package com.najacks.backend.tracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_daily_visits",
        indexes = @Index(name = "idx_visit_date", columnList = "visit_date")
)
@IdClass(UserDailyVisitId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyVisit {

    @Id
    @Column(name = "user_no", nullable = false)
    private Long userNo;

    @Id
    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "first_visit_at", nullable = false)
    private LocalDateTime firstVisitAt;

    @Column(name = "last_visit_at", nullable = false)
    private LocalDateTime lastVisitAt;

    @Column(name = "visit_count", nullable = false)
    private Integer visitCount;
}
