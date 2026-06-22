package com.project.back.domain.email.repository;

import com.project.back.domain.email.entity.EmailSend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmailSendRepository extends JpaRepository<EmailSend, Long> {

    @Query("SELECT e FROM EmailSend e JOIN FETCH e.quote " +
            "WHERE e.sentBy.id = :userId ORDER BY e.createdAt DESC")
    List<EmailSend> findBySentByIdWithQuote(@Param("userId") Long userId);
}
