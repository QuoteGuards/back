package com.project.back.domain.user.repository;

import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    List<User> findByRoleAndDepartmentAndStatus(UserRole role, String department, UserStatus status);

    Optional<User> findByMemberNumber(String memberNumber);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByMemberNumber(String memberNumber);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:keyword IS NULL OR u.name LIKE %:keyword% OR u.memberNumber LIKE %:keyword% OR u.email LIKE %:keyword%)
            """)
    Page<User> findAllWithFilters(
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
