package com.najacks.backend.domain.notification.service;

import com.najacks.backend.domain.notification.dto.NotificationResponse;
import com.najacks.backend.domain.notification.entity.Notification;
import com.najacks.backend.domain.notification.entity.NotificationType;
import com.najacks.backend.domain.notification.repository.NotificationRepository;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalse(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Notification updated = Notification.builder()
                .id(notification.getId())
                .user(notification.getUser())
                .type(notification.getType())
                .message(notification.getMessage())
                .referenceId(notification.getReferenceId())
                .isRead(true)
                .build();
        notificationRepository.save(updated);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        for (Notification n : unread) {
            Notification updated = Notification.builder()
                    .id(n.getId()).user(n.getUser()).type(n.getType())
                    .message(n.getMessage()).referenceId(n.getReferenceId())
                    .isRead(true).build();
            notificationRepository.save(updated);
        }
    }

    @Transactional
    public void createNotification(Long userId, NotificationType type, String message, Long referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }
}
