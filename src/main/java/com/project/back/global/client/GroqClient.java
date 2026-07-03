package com.project.back.global.client;

import com.project.back.global.client.dto.GroqRequest;
import com.project.back.global.client.dto.GroqResponse;
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
public class GroqClient {

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    private final RestClient restClient;
    private final String apiKey;

    public GroqClient(@Value("${groq.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    public String generateContent(String prompt) {
        try {
            GroqResponse response = restClient.post()
                    .uri(BASE_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GroqRequest.of(MODEL, prompt))
                    .retrieve()
                    .body(GroqResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
            }

            return response.extractText();

        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Groq API 호출 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        }
    }
}
