package com.project.back.domain.user.repository;

import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMemberNumber(String memberNumber);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByMemberNumber(String memberNumber);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /**
     * 특정 연도 접두사로 시작하는 회원번호 중 가장 큰 값을 반환.
     * 예: prefix = "2026" → "2026001", "2026002" 중 최댓값 반환
     */
    @Query("SELECT MAX(u.memberNumber) FROM User u WHERE u.memberNumber LIKE CONCAT(:prefix, '%')")
    Optional<String> findMaxMemberNumberByYearPrefix(@Param("prefix") String prefix);

    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:keyword IS NULL OR u.name LIKE %:keyword% OR u.memberNumber LIKE %:keyword%)
            """)
    Page<User> findAllWithFilters(
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
