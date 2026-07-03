package com.project.back.global.client.dto;

import java.util.List;

public record GroqRequest(String model, List<Message> messages, double temperature) {

    public record Message(String role, String content) {}

    // temperature를 낮게 고정 — 한국어 출력에 다른 언어(베트남어/일본어 등)가 섞여 나오는 이탈 방지
    private static final double TEMPERATURE = 0.3;

    public static GroqRequest of(String model, String prompt) {
        return new GroqRequest(model, List.of(new Message("user", prompt)), TEMPERATURE);
    }
}
