package com.dyk.new_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dyk.new_app.adapter.MessageAdapter;
import com.dyk.new_app.database.AppDatabase;
import com.dyk.new_app.entity.ConversationEntity;
import com.dyk.new_app.entity.Message;
import com.dyk.new_app.entity.MessageEntity;
import com.dyk.new_app.llm_chat.LLMApiClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 2;
    private static final int REQUEST_RECORD_AUDIO = 3;

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;
    private EditText messageEditText;
    private ImageButton imageButton, voiceButton, sendButton;
    private AppDatabase database;
    private ExecutorService executorService;
    private long currentConversationId = -1; // 默认值，表示新对话
    private ImageButton backButton;
    private ImageButton menuButton;
    private boolean isNewAndEmpty = false;
    private String pendingImagePath = null; // 存储临时图片路径
    private View attachedImageViewContainer; // 用于显示临时图片的容器 (需要在布局中添加)
    private LLMApiClient llmApiClient; // 添加 LLMApiClient 实例
    private Message thinkingMessage = null;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取传递过来的 conversation ID
        Intent intent = getIntent();
        currentConversationId = intent.getLongExtra("CONVERSATION_ID", -1);

        database = AppDatabase.getDatabase(getApplicationContext());
        executorService = Executors.newFixedThreadPool(2);
        llmApiClient = new LLMApiClient(); // 初始化 LLMApiClient

        initializeViews();
        setupRecyclerView();
        setupClickListeners();

        if (currentConversationId != -1) {
            // 加载现有对话
            loadConversation(currentConversationId);
        } else {
            // 创建新对话
            createNewConversation();
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
    }

    private void initializeViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        imageButton = findViewById(R.id.imageButton);
        voiceButton = findViewById(R.id.voiceButton);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.back_button);
        menuButton = findViewById(R.id.menu_button);
        attachedImageViewContainer = findViewById(R.id.attachedImageContainer);
    }

    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList,MainActivity.this);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> {
            String text = messageEditText.getText().toString().trim();
            boolean hasText = !TextUtils.isEmpty(text);
            boolean hasImage = pendingImagePath != null;

            if (hasText || hasImage) { // 至少有文本或图片

                String imagePathToSend = pendingImagePath; // 获取待发送的图片路径
                String textToSend = text; // 获取待发送的文本

                // 清空输入框和临时图片
                messageEditText.setText("");
                removeAttachedImage(); // 隐藏UI并清空 pendingImagePath

                // 关闭键盘
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);

                // 添加消息到适配器和数据库
                // 注意：这里需要决定如何处理同时有文本和图片的消息
                // 选项1: 发送一条包含文本和图片路径的消息
                // 选项2: 如果有图片，先发图片消息，再发文本消息（如果有的话）
                // 选项3: 如果有图片，先发文本消息（如果有的话），再发图片消息
                // 这里采用选项1，您可以根据 LLM API 的支持情况调整
                addMessageToAdapter(textToSend, true, imagePathToSend);

                // 添加 ai 思考信息
                addThinkingMessageToAdapter();

                // 调用 LLM API 发送消息
                llmApiClient.sendMessage(messageList, new LLMApiClient.LLMCallback() {
                    @Override
                    public void onSuccess(String responseText) {
                        // 移除 ai 思考信息
                        removeThinkingMessageFromAdapter();
                        // 在主线程上执行
                        addMessageToAdapter(responseText, false, null); // 添加 AI 响应
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // 移除 ai 思考信息
                        removeThinkingMessageFromAdapter();
                        // 在主线程上执行
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        // 可选：添加错误消息到 UI
                        addMessageToAdapter("错误: " + errorMessage, false, null);
                    }
                });


            }
        });

        imageButton.setOnClickListener(v -> {
            openImagePicker(); // 打开相册即可
            // 可选：添加相机拍照
        });

        voiceButton.setOnClickListener(v -> {
            // 检查录音权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            } else {
                // TODO: 在这里启动语音识别
                // 例如，启动一个录音服务或使用SpeechRecognizer API
                // 假设识别后得到文本 "Voice input received"
                // addMessageToAdapter("Voice input received", true, null); // isUser = true
            }
        });

        backButton.setOnClickListener(v->{
            // 返回上一页
            finish();

        });

        menuButton.setOnClickListener(v->{
            // 启动 SettingsActivity，并传递当前对话 ID
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            settingsIntent.putExtra("CONVERSATION_ID", currentConversationId);
            startActivity(settingsIntent);
        });
    }

    // 👇 新增：添加“思考中”消息到适配器
    private void addThinkingMessageToAdapter() {
        thinkingMessage = new Message(null, true);
        Log.d("MainActivity", "Adding thinking message: " + thinkingMessage.getId() + ", isThinking: " + thinkingMessage.isThinking());
        messageList.add(thinkingMessage);
        Log.d("MainActivity", "Message list size after adding thinking: " + messageList.size());
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        messagesRecyclerView.scrollToPosition(messageList.size() - 1);
    }

    // 👇 新增：从适配器移除“思考中”消息
    private void removeThinkingMessageFromAdapter() {
        if (thinkingMessage != null) {
            int position = messageList.indexOf(thinkingMessage);
            if (position != -1) {
                messageList.remove(position);
                messageAdapter.notifyItemRemoved(position);
                // thinkingMessage 已被移除，置为 null 以便下次使用
                thinkingMessage = null;
            } else {
                Log.w(TAG, "Thinking message not found in list during removal.");
            }
        }
        // 如果 thinkingMessage 为 null，说明之前可能已经移除过（例如网络错误后又收到回复），或者从未添加过，无需操作。
    }

    private void showAttachedImagePreview(String imagePath) {
        ImageView attachedImageView = findViewById(R.id.attachedImageView);

        // 加载并设置图片 (考虑使用 Glide 或 Picasso 以避免 OOM)
        // 这里使用 BitmapFactory，注意处理大图
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            attachedImageView.setImageBitmap(bitmap);
            attachedImageView.setVisibility(View.VISIBLE);
        } else {
            attachedImageView.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load image preview.", Toast.LENGTH_SHORT).show();
            return; // 如果加载失败，可能不需要显示容器
        }

        // 显示容器
        attachedImageViewContainer.setVisibility(View.VISIBLE);
    }

    private void removeAttachedImage() {
        pendingImagePath = null; // 清空路径
        ImageView attachedImageView = findViewById(R.id.attachedImageView);
        attachedImageView.setImageBitmap(null); // 清空 ImageView
        attachedImageViewContainer.setVisibility(View.GONE); // 隐藏容器
    }

    private void openImagePicker() {
        // 只创建从相册选择图片的 Intent
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // 直接启动选择图片的 Intent
        startActivityForResult(pickImageIntent, REQUEST_IMAGE_PICK);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予，可以开始录音
            } else {
                // 权限被拒绝，可以提示用户
                Toast.makeText(this, "权限不足，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String imagePath = null; // 使用 String 存储路径

            if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                try {
                    Uri imageUri = data.getData();
                    // 获取文件输入流
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    if (inputStream != null) {
                        // 将图片复制到应用私有目录
                        imagePath = copyImageToPrivateStorage(inputStream);
                        inputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "处理图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            // 显示图片缩略图
            if (imagePath != null) {
                // 存储路径
                pendingImagePath = imagePath;
                // 显示图片预览
                showAttachedImagePreview(imagePath);
            }
        }
    }

    private String copyImageToPrivateStorage(InputStream inputStream) {
        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // 获取应用私有图片目录

        if (storageDir != null) {
            File imageFile = new File(storageDir, fileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(imageFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                fileOutputStream.flush();
                // 返回文件的绝对路径
                return imageFile.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                // 可能需要删除部分写入的文件
                if (imageFile.exists()) {
                    imageFile.delete();
                }
            }
        }
        return null; // 复制失败
    }


    private void addMessageToAdapter(String text, boolean isUser, @Nullable String imagePath) {
        Message message = new Message(text, isUser, imagePath); // 传入路径
        messageList.add(message);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        messagesRecyclerView.scrollToPosition(messageList.size() - 1);

        // 保存消息到数据库
        if (currentConversationId != -1) {
            executorService.execute(() -> {
                // 传入路径而不是字节数组
                database.messageDao().insertMessage(
                        new MessageEntity(currentConversationId, text, isUser, imagePath, System.currentTimeMillis())
                );

                // 👇 如果这是第一次添加消息，更新标志
                if (isNewAndEmpty && isUser) { // 假设至少有一条用户消息才算非空
                    isNewAndEmpty = false;
                }
            });
        }
    }

    private void createNewConversation() {
        executorService.execute(() -> {
            ConversationEntity newConversation = new ConversationEntity("新对话", System.currentTimeMillis());
            long newId = database.conversationDao().insertConversation(newConversation);
            currentConversationId = newId;
            isNewAndEmpty = true;
            // 新对话创建后，messageList 是空的，UI 也清空
            runOnUiThread(() -> {
                messageList.clear(); // 清空当前列表
                messageAdapter.notifyDataSetChanged(); // 通知适配器数据已清空
            });
        });
    }

    private void loadConversation(long id) {
        executorService.execute(() -> {
            ConversationEntity convEntity = database.conversationDao().getConversationById(id);
            List<MessageEntity> messageEntities = database.messageDao().getMessagesForConversation(id);

            List<Message> messages = new ArrayList<>();
            for (MessageEntity entity : messageEntities) {
                // 使用 imagePath 构建 Message
                messages.add(new Message(entity.getText(), entity.isUser(), entity.getImagePath()));
            }

            final List<Message> finalMessages = messages;
            runOnUiThread(() -> {
                currentConversationId = id;
                messageList.clear();
                messageList.addAll(finalMessages);
                messageAdapter.notifyDataSetChanged();
                messagesRecyclerView.scrollToPosition(messageList.size() - 1);
            });
        });
    }

    // 👇 在 Activity 即将停止/销毁时检查是否需要删除空对话
    @Override
    protected void onPause() {
        super.onPause();
        if (currentConversationId != -1 && isNewAndEmpty) {
            // 当前对话是新创建的且仍然是空的，需要删除
            executorService.execute(() -> {
                database.conversationDao().deleteConversationById(currentConversationId);
                // 注意：删除对话会级联删除其消息，如果设置了级联删除的话
            });
        }
    }
}