package com.najacks.backend.domain.clip.service;

import com.najacks.backend.domain.clip.dto.ClipCreateRequest;
import com.najacks.backend.domain.clip.dto.ClipResponse;
import com.najacks.backend.domain.clip.entity.Clip;
import com.najacks.backend.domain.clip.repository.ClipRepository;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClipService {

    private final ClipRepository clipRepository;
    private final UserRepository userRepository;

    /**
     * 시간 가중 인기 클립 Top 10.
     * score = (viewCount + 1) / (hoursSinceCreated + 2)^1.5
     * → 갓 올린 클립도 노출 기회, 오래된 클립은 서서히 밀려남 (Hacker News 방식).
     */
    @Transactional(readOnly = true)
    public List<ClipResponse> getPopularClips() {
        LocalDateTime now = LocalDateTime.now();
        return clipRepository.findAll().stream()
                .sorted(Comparator.comparingDouble((Clip c) -> score(c, now)).reversed())
                .limit(10)
                .map(ClipResponse::from)
                .toList();
    }

    private double score(Clip clip, LocalDateTime now) {
        long hours = clip.getCreatedAt() != null
                ? Math.max(0, ChronoUnit.HOURS.between(clip.getCreatedAt(), now)) : 0;
        int v = clip.getViewCount() == null ? 0 : clip.getViewCount();
        return (v + 1) / Math.pow(hours + 2, 1.5);
    }

    @Transactional(readOnly = true)
    public List<ClipResponse> getStreamerClips(Long streamerId) {
        return clipRepository.findByStreamerId(streamerId).stream()
                .map(ClipResponse::from)
                .toList();
    }

    @Transactional
    public ClipResponse createClip(Long userId, ClipCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != Role.STREAMER && user.getRole() != Role.ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Clip clip = Clip.builder()
                .streamer(user)
                .title(request.title())
                .url(request.url())
                .thumbnailUrl(request.thumbnailUrl())
                .viewCount(0)
                .build();

        Clip saved = clipRepository.save(clip);
        return ClipResponse.from(saved);
    }

    @Transactional
    public void increaseViewCount(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("클립을 찾을 수 없습니다"));
        clip.increaseViewCount();
    }

    @Transactional
    public void deleteClip(Long clipId, Long userId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("클립을 찾을 수 없습니다"));

        if (!clip.getStreamer().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        clipRepository.delete(clip);
    }
}
