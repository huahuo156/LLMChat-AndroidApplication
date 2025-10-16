package com.dyk.new_app.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.dyk.new_app.entity.ConversationEntity;

import java.util.List;

@Dao
public interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    List<ConversationEntity> getAllConversations();

    @Insert
    long insertConversation(ConversationEntity conversation);

    @Update
    void updateConversation(ConversationEntity conversation);

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    void deleteConversation(long conversationId);

    // 获取最新对话的ID
    @Query("SELECT id FROM conversations ORDER BY timestamp DESC LIMIT 1")
    Long getLatestConversationId();

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    ConversationEntity getConversationById(long id);

    @Query("DELETE FROM conversations WHERE id = :id")
    void deleteConversationById(long id);

    @Delete
    void deleteConversation(ConversationEntity conversation);

    // 👇 新增：更新对话的系统提示词
    @Query("UPDATE conversations SET systemPrompt = :systemPrompt WHERE id = :conversationId")
    void updateSystemPromptForConversation(long conversationId, String systemPrompt);
}
