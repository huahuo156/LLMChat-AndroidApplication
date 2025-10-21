package com.dyk.new_app.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
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
    private String filePath; // 👈 新增：文件路径
    private String fileMimeType; // 👈 新增：文件 MIME 类型

    // Constructors

    // 构造函数 (包含文件)
    public MessageEntity(long conversationId, String text, boolean isUser, String imagePath, String filePath, String fileMimeType, long timestamp) { // 👈 修改构造函数
        this.conversationId = conversationId;
        this.text = text;
        this.isUser = isUser;
        this.imagePath = imagePath;
        this.filePath = filePath; // 👈 设置文件路径
        this.fileMimeType = fileMimeType; // 👈 设置文件类型
        this.timestamp = timestamp;
    }

    @Ignore
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

    public String getFilePath() { return filePath; } // 👈 Getter for filePath
    public void setFilePath(String filePath) { this.filePath = filePath; } // 👈 Setter for filePath

    public String getFileMimeType() { return fileMimeType; } // 👈 Getter for fileMimeType
    public void setFileMimeType(String fileMimeType) { this.fileMimeType = fileMimeType; } // 👈 Setter for fileMimeType
}
