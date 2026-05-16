package com.najacks.backend.domain.chat.repository;

import com.najacks.backend.domain.chat.entity.EmailReportLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailReportLogRepository extends JpaRepository<EmailReportLog, Long> {
}
