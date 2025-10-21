package com.dyk.new_app.llm_chat;

import com.google.gson.annotations.SerializedName;

public class ChatRequest {
    @SerializedName("message")
    private String message;
    @SerializedName("system_prompt")
    private String system_prompt;
    @SerializedName("session_id")
    private String session_id;

    public ChatRequest(String session_id,String message,String system_prompt) {
        this.session_id = session_id;
        this.message = message;
        this.system_prompt = system_prompt;
    }

    public ChatRequest() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取
     * @return system_prompt
     */
    public String getSystem_prompt() {
        return system_prompt;
    }

    /**
     * 设置
     * @param system_prompt
     */
    public void setSystem_prompt(String system_prompt) {
        this.system_prompt = system_prompt;
    }

    public String toString() {
        return "ChatRequest{message = " + message + ", system_prompt = " + system_prompt + "}";
    }

    /**
     * 获取
     * @return session_id
     */
    public String getSession_id() {
        return session_id;
    }

    /**
     * 设置
     * @param session_id
     */
    public void setSession_id(String session_id) {
        this.session_id = session_id;
    }
}