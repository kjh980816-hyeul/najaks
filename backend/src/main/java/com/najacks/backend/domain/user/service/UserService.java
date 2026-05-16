package com.najacks.backend.domain.user.service;

import com.najacks.backend.domain.user.dto.StreamerProfileDto;
import com.najacks.backend.domain.user.dto.UserProfileResponse;
import com.najacks.backend.domain.user.dto.UserUpdateRequest;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StreamerProfileRepository streamerProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StreamerProfileDto streamerProfile = null;
        if (user.getRole() == Role.STREAMER) {
            streamerProfile = streamerProfileRepository.findByUserId(userId)
                    .map(StreamerProfileDto::from)
                    .orElse(null);
        }

        return UserProfileResponse.from(user, streamerProfile);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
            }
            user.setNickname(request.nickname());
        }

        if (request.profileImage() != null) {
            user.setProfileImage(request.profileImage());
        }

        userRepository.save(user);

        StreamerProfileDto streamerProfile = null;
        if (user.getRole() == Role.STREAMER) {
            streamerProfile = streamerProfileRepository.findByUserId(userId)
                    .map(StreamerProfileDto::from)
                    .orElse(null);
        }

        return UserProfileResponse.from(user, streamerProfile);
    }
}
