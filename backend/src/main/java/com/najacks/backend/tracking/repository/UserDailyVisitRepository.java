package com.najacks.backend.tracking.repository;

import com.najacks.backend.tracking.entity.UserDailyVisit;
import com.najacks.backend.tracking.entity.UserDailyVisitId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface UserDailyVisitRepository
        extends JpaRepository<UserDailyVisit, UserDailyVisitId> {

    @Modifying
    @Query(value = """
            INSERT INTO user_daily_visits
                (user_no, visit_date, first_visit_at, last_visit_at, visit_count)
            VALUES
                (:userNo, :visitDate, NOW(), NOW(), 1)
            ON DUPLICATE KEY UPDATE
                last_visit_at = NOW(),
                visit_count = visit_count + 1
            """, nativeQuery = true)
    void upsertVisit(@Param("userNo") Long userNo,
                     @Param("visitDate") LocalDate visitDate);

    @Query(value = "SELECT COUNT(*) FROM user_daily_visits WHERE visit_date = :date",
            nativeQuery = true)
    long countDau(@Param("date") LocalDate date);

    @Query(value = """
            SELECT COUNT(DISTINCT user_no) FROM user_daily_visits
            WHERE visit_date BETWEEN :from AND :to
            """, nativeQuery = true)
    long countActiveUsersInRange(@Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
