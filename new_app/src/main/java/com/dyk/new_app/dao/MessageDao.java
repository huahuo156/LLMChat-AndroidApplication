package com.dyk.new_app.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.dyk.new_app.entity.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    List<MessageEntity> getMessagesForConversation(long conversationId);

    @Insert
    long insertMessage(MessageEntity message);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    void deleteMessagesForConversation(long conversationId);
}
