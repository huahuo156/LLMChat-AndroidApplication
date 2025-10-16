package com.dyk.new_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.dyk.new_app.database.AppDatabase;
import com.dyk.new_app.entity.ConversationEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private Button clearConversationButton;
    private EditText systemPromptEditText;
    private Button saveSystemPromptButton;
    private AppDatabase database;
    private ExecutorService executorService;
    private long currentConversationId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        currentConversationId = getIntent().getLongExtra("CONVERSATION_ID", -1);

        database = AppDatabase.getDatabase(getApplicationContext());
        executorService = Executors.newFixedThreadPool(2);

        initializeViews();
        setupClickListeners();
        loadSystemPrompt(); // 从数据库加载当前对话的系统提示词
    }

    private void initializeViews() {
        clearConversationButton = findViewById(R.id.clearConversationButton);
        systemPromptEditText = findViewById(R.id.systemPromptEditText);
        saveSystemPromptButton = findViewById(R.id.saveSystemPromptButton);
    }

    private void setupClickListeners() {
        clearConversationButton.setOnClickListener(v -> {
            if (currentConversationId != -1) {
                showClearConfirmationDialog();
            } else {
                Toast.makeText(this, "当前为新对话，无需清除", Toast.LENGTH_SHORT).show();
            }
        });

        saveSystemPromptButton.setOnClickListener(v -> {
            if (currentConversationId != -1) {
                String newPrompt = systemPromptEditText.getText().toString().trim();
                // 保存到数据库
                executorService.execute(() -> {
                    database.conversationDao().updateSystemPromptForConversation(currentConversationId, newPrompt);
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, "系统提示词已保存", Toast.LENGTH_SHORT).show();
                    });
                });
            } else {
                Toast.makeText(this, "无法为新对话设置系统提示词", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showClearConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清除对话")
                .setMessage("确定要清除当前对话的所有消息吗？此操作不可撤销。")
                .setPositiveButton("确定", (dialog, which) -> {
                    executorService.execute(() -> {
                        database.messageDao().deleteMessagesForConversation(currentConversationId);
                        // 如果需要，也可以删除对话本身
                        // database.conversationDao().deleteConversationById(currentConversationId);

                        runOnUiThread(() -> {
                            Toast.makeText(SettingsActivity.this, "对话已清除", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadSystemPrompt() {
        if (currentConversationId != -1) {
            executorService.execute(() -> {
                ConversationEntity conversation = database.conversationDao().getConversationById(currentConversationId);
                String savedPrompt = conversation != null ? conversation.getSystemPrompt() : "";
                runOnUiThread(() -> {
                    systemPromptEditText.setText(savedPrompt);
                });
            });
        } else {
            // 对于新对话，EditText 保持空白
            systemPromptEditText.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}