package com.dyk.new_app.entity;

import java.util.UUID;

public class Message {
    private String text;
    private boolean isUser; // true for user, false for AI
    private String imagePath;
    private String id;
    public boolean isThinking;

    public Message(String text, boolean isUser, String imagePath) {
        this.text = text;
        this.isUser = isUser;
        this.imagePath = imagePath;
        this.id = UUID.randomUUID().toString(); // 生成一个随机的 UUID 字符串
        this.isThinking = false;
    }

    public Message(String id, boolean isThinking) { // 可以考虑传入一些描述信息，但这里简化
        this.text = "AI 正在思考..."; // 或者可以不设置 text，由 Adapter 处理
        this.isUser = false; // 思考状态属于 AI
        this.imagePath = null; // 思考状态无图片
        this.id = id != null ? id : UUID.randomUUID().toString(); // 可以传入一个 ID，或自动生成
        this.isThinking = isThinking; // 设置为 true
    }

    public String getText() {
        return text;
    }

    public void setText(String text){
        this.text = text;
    }

    public boolean isUser() {
        return isUser;
    }

    public String getImagePath() {
        return imagePath;
    }
    public String getId(){return this.id;}
    public void setId(String id){this.id = id;}

    public boolean isThinking(){return this.isThinking;}
    public void setThinking(boolean isThinking){this.isThinking=isThinking;}

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Message message = (Message) obj;
        return id.equals(message.id); // 基于 ID 判断相等
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
