package com.project.back.global.client.dto;

import java.util.List;

public record GroqResponse(List<Choice> choices) {

    public record Choice(Message message) {}

    public record Message(String role, String content) {}

    public String extractText() {
        return choices.get(0).message().content();
    }
}
