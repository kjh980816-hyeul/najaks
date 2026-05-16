package com.najacks.backend.auth.service;

import com.najacks.backend.auth.dto.*;
import com.najacks.backend.auth.jwt.JwtTokenProvider;
import com.najacks.backend.domain.user.entity.PasswordResetToken;
import com.najacks.backend.domain.user.entity.Provider;
import com.najacks.backend.domain.user.entity.Role;
import com.najacks.backend.domain.user.entity.StreamerProfile;
import com.najacks.backend.domain.user.entity.User;
import com.najacks.backend.domain.user.repository.PasswordResetTokenRepository;
import com.najacks.backend.domain.user.repository.StreamerProfileRepository;
import com.najacks.backend.domain.user.repository.UserRepository;
import com.najacks.backend.infra.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StreamerProfileRepository streamerProfileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + request.email());
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + request.nickname());
        }

        // STREAMER도 그대로 부여 (스트리머 프로필은 verified=false로 생성, 관리자 승인 후 verified=true)
        Role role = request.role() != null ? request.role() : Role.FAN;

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .role(role)
                .provider(Provider.LOCAL)
                .build();

        User savedUser = userRepository.save(user);

        // 스트리머로 가입 시 프로필 자동 생성 (verified=false, 선택한 플랫폼 마크 저장)
        if (role == Role.STREAMER) {
            StreamerProfile.StreamerProfileBuilder profileBuilder = StreamerProfile.builder()
                    .user(savedUser)
                    .category(request.category())
                    .verified(false);

            if (Boolean.TRUE.equals(request.platformYoutube())) {
                profileBuilder.youtubeUrl("https://youtube.com");
            }
            if (Boolean.TRUE.equals(request.platformChzzk())) {
                profileBuilder.chzzkUrl("https://chzzk.naver.com");
            }
            if (Boolean.TRUE.equals(request.platformSoop())) {
                profileBuilder.soopUrl("https://www.sooplive.co.kr");
            }

            streamerProfileRepository.save(profileBuilder.build());
        }

        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getId());

        return buildAuthResponse(accessToken, refreshToken, savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);

        return buildAuthResponse(newAccessToken, request.refreshToken(), user);
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        return new AuthResponse.UserInfo(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getRole()
        );
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // 보안: 존재 여부 노출 방지. 성공처럼 그대로 리턴
            log.info("Password reset requested for non-existing email: {}", email);
            return;
        }
        if (user.getProvider() != Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호 재설정이 불가능합니다");
        }

        passwordResetTokenRepository.invalidateAllForUser(user.getId());

        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getNickname(), token);
    }

    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        return passwordResetTokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다");
        }
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 토큰입니다");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getRole()
        );
        return new AuthResponse(accessToken, refreshToken, userInfo);
    }
}
