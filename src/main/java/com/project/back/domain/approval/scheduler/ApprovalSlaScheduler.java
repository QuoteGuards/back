package com.project.back.domain.approval.scheduler;

import com.project.back.domain.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSlaScheduler {

    private final ApprovalService approvalService;

    @Value("${approval.sla.days:2}")
    private int slaDays;

    // 매일 오전 9시 실행 — PENDING 상태로 SLA(기본 2일)를 초과한 승인 요청을 담당자에게 알린다.
    @Scheduled(cron = "0 0 9 * * *")
    public void notifySlaBreaches() {
        log.info("승인 SLA 초과 알림 스케줄러 시작 [기준일={}일]", slaDays);
        approvalService.notifySlaBreaches(slaDays);
    }
}
