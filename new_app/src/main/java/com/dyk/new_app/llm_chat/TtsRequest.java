package com.dyk.new_app.llm_chat;

public class TtsRequest {
    private String text;

    public TtsRequest(String text) {
        this.text = text;
    }

    // Getters and Setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
