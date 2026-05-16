package com.najacks.backend.domain.admin.repository;

import com.najacks.backend.domain.admin.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByPinnedTrue();
}
