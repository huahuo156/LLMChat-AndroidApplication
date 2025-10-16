package com.dyk.new_app.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey; // 用于外键关联

@Entity(tableName = "messages",
        foreignKeys = @ForeignKey(
                entity = ConversationEntity.class,
                parentColumns = "id",
                childColumns = "conversationId"
        ),
        indices = {@Index(value = "conversationId")}
)
public class MessageEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long conversationId; // 关联到 Conversation
    private String text;
    private boolean isUser; // true for user, false for AI
    private String imagePath; // 可选的图片数据
    private long timestamp; // 消息时间戳

    // Constructors
    public MessageEntity(long conversationId, String text, boolean isUser, String imagePath, long timestamp) {
        this.conversationId = conversationId;
        this.text = text;
        this.isUser = isUser;
        this.imagePath = imagePath;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isUser() { return isUser; }
    public void setUser(boolean user) { isUser = user; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
