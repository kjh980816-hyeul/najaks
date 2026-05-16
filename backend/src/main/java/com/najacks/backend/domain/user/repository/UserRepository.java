package com.najacks.backend.domain.user.repository;

import com.najacks.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("SELECT u FROM User u WHERE LOWER(u.nickname) LIKE :nick OR LOWER(u.email) LIKE :email")
    List<User> findByNicknameLikeOrEmailLike(@Param("nick") String nickname, @Param("email") String email);
}
