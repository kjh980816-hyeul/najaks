package com.najacks.backend.domain.chat.repository;

import com.najacks.backend.domain.chat.entity.ChatHighlight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHighlightRepository extends JpaRepository<ChatHighlight, Long> {
    List<ChatHighlight> findByStreamIdOrderByOccurredAtAsc(String streamId);
}
