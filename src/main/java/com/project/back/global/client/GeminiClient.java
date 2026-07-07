package com.project.back.global.client;

import com.project.back.global.client.dto.GeminiRequest;
import com.project.back.global.client.dto.GeminiResponse;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiClient {

    private static final String BASE_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;

    // gemini-2.0-flash는 2026-06-01부로 종료(deprecated)되어 고정하지 않고 설정으로 모델을 주입받는다.
    public GeminiClient(@Value("${gemini.api-key}") String apiKey,
                         @Value("${gemini.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = BASE_URL_TEMPLATE.formatted(model);
        this.restClient = RestClient.create();
    }

    public String generateContent(String prompt) {
        try {
            // 최근 발급되는 AQ. 접두사 인증키는 ?key= 쿼리 파라미터 방식으로는 403이 발생하므로
            // 구글 권장 방식인 x-goog-api-key 헤더로 전달한다 (기존 AIza 키와도 호환됨).
            GeminiResponse response = restClient.post()
                    .uri(baseUrl)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(prompt))
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null
                    || response.candidates() == null
                    || response.candidates().isEmpty()
                    || response.candidates().get(0).content() == null
                    || response.candidates().get(0).content().parts() == null
                    || response.candidates().get(0).content().parts().isEmpty()) {
                throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
            }

            return response.extractText();

        } catch (CustomException e) {
            throw e;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            log.error("Gemini API 호출 실패: status={}, body={}", status, e.getResponseBodyAsString());
            if (status.value() == 429) {
                throw new CustomException(ErrorCode.AI_SUMMARY_RATE_LIMITED);
            }
            throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        } catch (RuntimeException e) {
            log.error("Gemini API 호출 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        }
    }
}
