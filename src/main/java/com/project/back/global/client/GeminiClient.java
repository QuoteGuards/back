package com.project.back.global.client;

import com.project.back.global.client.dto.GeminiRequest;
import com.project.back.global.client.dto.GeminiResponse;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class GeminiClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final RestClient restClient;
    private final String apiKey;

    public GeminiClient(@Value("${gemini.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    public String generateContent(String prompt) {
        try {
            GeminiResponse response = restClient.post()
                    .uri(BASE_URL + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(prompt))
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
            }

            return response.extractText();

        } catch (RestClientException e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        }
    }
}
