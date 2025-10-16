package com.dyk.new_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dyk.new_app.adapter.ConversationsAdapter;
import com.dyk.new_app.database.AppDatabase;
import com.dyk.new_app.entity.ConversationEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationsActivity extends AppCompatActivity {

    private RecyclerView conversationsRecyclerView;
    private ConversationsAdapter conversationsAdapter;
    private List<ConversationEntity> conversationList;
    private AppDatabase database;
    private ExecutorService executorService;
    private Button newConversationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        database = AppDatabase.getDatabase(getApplicationContext());
        executorService = Executors.newFixedThreadPool(2); // 用于数据库操作

        initializeViews();
        loadConversations(); // 从数据库加载对话列表
        setupClickListeners();
    }

    private void initializeViews() {
        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView);
        newConversationButton = findViewById(R.id.newConversationButton);

        conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadConversations() {
        executorService.execute(() -> {
            conversationList = database.conversationDao().getAllConversations();
            runOnUiThread(() -> {
                conversationsAdapter = new ConversationsAdapter(conversationList, this);
                conversationsRecyclerView.setAdapter(conversationsAdapter);
            });
        });
    }

    private void setupClickListeners() {
        newConversationButton.setOnClickListener(v -> {
            // 创建新对话并跳转到 MainActivity
            executorService.execute(() -> {
                long newConversationId = database.conversationDao().insertConversation(
                        new ConversationEntity("新对话", System.currentTimeMillis())
                );
                Intent intent = new Intent(ConversationsActivity.this, MainActivity.class);
                intent.putExtra("CONVERSATION_ID", newConversationId);
                startActivity(intent);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次 Activity 重新可见时（例如从 MainActivity 返回），都重新加载列表
        // 这样可以反映出 MainActivity 中可能删除的空对话
        loadConversations();
    }
}
