package com.project.back.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 영상 Range 요청 등에서 브라우저가 연결을 끊을 때 발생하는 예외 처리.
 * 정적 리소스(/uploads/**) 스트리밍 중에도 적용되도록 RestController 범위 밖에서 처리한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ClientDisconnectExceptionHandler {

    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientDisconnect(Exception e) {
        log.debug("Client disconnected during response: {}", e.getMessage());
    }

    static boolean isBenignClientDisconnect(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ClientAbortException
                    || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (
                    message.contains("Broken pipe")
                            || message.contains("Connection reset")
                            || message.contains("현재 연결은 사용자의 호스트 시스템"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
