package com.najacks.backend.domain.chat.repository;

import com.najacks.backend.domain.chat.entity.EmailUnsubscribeToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailUnsubscribeTokenRepository extends JpaRepository<EmailUnsubscribeToken, String> {
}
