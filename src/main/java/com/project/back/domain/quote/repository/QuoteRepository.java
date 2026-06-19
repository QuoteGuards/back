package com.project.back.domain.quote.repository;

import om.p

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.user.entity.User;import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query
import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long>, QuoteRepositoryCustom {

    // 내 견적 목록 (상태별 필터, 최신 버전만)
    List<Quote> findByCreatedByIdAndStatusAndIsLatestTrueOrderByCreatedAtDesc(
           



        List<Quote> findByCreatedBy
        
                         조회

                "JOIN FETCH
                "LEFT JOIN FETCH q.items " +

        Optional<Qu
        
                        SELECT DISTINCT q FROM Quo
                        "LEFT JOIN FETCH q.approvalR
                        "WHERE q.id = :id")
        Optional<Quote> findByIdWithApprovalReasons(@Param("id") L

        // 원본 견적 기준 버전 이력 조회 (재사용/재작성 이력)
                        te> findByOriginalQuoteIdOrderByVersio
                        
        // 견적번호로 조회 (중복 방지 체크용)

        
        // 만료 처리 대상 조회 (배치 또는 스케줄러용)

                "WHERE q.validU
                "AND q.status IN :statuses")

        
        List<Quote> findByCreatedByOrderB
                        
                        <Quote> findByQuoteNumber(St
            Opt

        

                Optional<String> findMaxQuoteNumberByPrefix(@Param("prefix") Str

                

                

                