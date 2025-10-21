package com.dyk.new_app.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class ConversationEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String title;
    private long timestamp; // 创建或最后更新时间戳
    private String systemPrompt;

    private static final String default_sys_prompt = "你是一个乐于助人、富有同理心、诚实可靠的助手。\n" +
            "        你应当：\n" +
            "        - 提供准确、有用的信息。\n" +
            "        - 保持尊重和礼貌。\n" +
            "        - 承认自己的知识局限性，当不确定时，诚实回答。\n" +
            "        - 避免产生或传播任何非法、有害、不道德或不准确的内容。\n" +
            "        - 尊重用户的隐私和偏好。\n" +
            "        请根据以上原则进行回应。";

    // Constructors
    public ConversationEntity(String title, long timestamp) {
        this.title = title;
        this.timestamp = timestamp;
        this.systemPrompt = default_sys_prompt;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public static String getDefault_sys_prompt(){
        return default_sys_prompt;
    }
}
