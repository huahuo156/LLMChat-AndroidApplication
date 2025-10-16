package com.dyk.new_app.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import com.dyk.new_app.dao.ConversationDao;
import com.dyk.new_app.dao.MessageDao;
import com.dyk.new_app.entity.ConversationEntity;
import com.dyk.new_app.entity.MessageEntity;

@Database(entities = {ConversationEntity.class, MessageEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    public abstract ConversationDao conversationDao();
    public abstract MessageDao messageDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "app_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
