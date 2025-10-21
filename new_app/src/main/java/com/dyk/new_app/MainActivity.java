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
import android.widget.TextView;
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
import com.dyk.new_app.llm_chat.ApiClient;
import com.dyk.new_app.llm_chat.ChatResponse;
import com.dyk.new_app.util.FileUtils;
import com.dyk.new_app.util.ImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SETTINGS = 1; // 定义请求设置码
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final int REQUEST_RECORD_AUDIO = 3;
    private static final int REQUEST_FILE_PICK = 4; // 文件选择请求码

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;
    private EditText messageEditText;
    private ImageButton imageButton, removeImageButton, voiceButton, sendButton, fileButton, removeFileButton;
    private AppDatabase database;
    private ExecutorService executorService;
    private long currentConversationId = -1; // 默认值，表示新对话
    private ImageButton backButton;
    private ImageButton menuButton;
    private boolean isNewAndEmpty = false;
    private String pendingImagePath = null; // 存储临时图片路径
    private View attachedImageViewContainer; // 用于显示临时图片的容器
    private View attachedFileContainer;
    private Message thinkingMessage = null;
    private String systemPrompt = null;
    private static final String TAG = "MainActivity";
    private String pendingFilePath = null; // 存储临时文件路径
    private String pendingFileMimeType = null; // 存储临时文件的 MIME 类型

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取传递过来的 conversation ID
        Intent intent = getIntent();
        currentConversationId = intent.getLongExtra("CONVERSATION_ID", -1);

        database = AppDatabase.getDatabase(getApplicationContext());
        executorService = Executors.newFixedThreadPool(2);

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
        fileButton = findViewById(R.id.fileButton);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.back_button);
        menuButton = findViewById(R.id.menu_button);
        attachedImageViewContainer = findViewById(R.id.attachedImageContainer);
        attachedFileContainer = findViewById(R.id.attachedFileContainer);
        removeFileButton = findViewById(R.id.removeFileButton);
        removeImageButton = findViewById(R.id.removeImageButton);
    }

    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList, MainActivity.this);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> {
            String text = messageEditText.getText().toString().trim();
            boolean hasText = !TextUtils.isEmpty(text);
            boolean hasImage = pendingImagePath != null;
            boolean hasFile = pendingFilePath != null;

            if (hasText || hasImage || hasFile) { // 至少有文本或图片或文件

                String imagePathToSend = pendingImagePath; // 获取待发送的图片路径
                String textToSend = text; // 获取待发送的文本
                String filePathToSend = pendingFilePath; // 获取待发送的文件路径
                String fileMimeTypeToSend = pendingFileMimeType; //  获取待发送的文件类型

                // 清空输入框、临时图片、临时文件
                messageEditText.setText("");
                removeAttachedImage(); // 隐藏UI并清空 pendingImagePath
                removeAttachedFile(); //  清空临时文件信息

                // 关闭键盘
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);

                // 添加消息到适配器和数据库
                addMessageToAdapter(textToSend, true, imagePathToSend, filePathToSend, fileMimeTypeToSend);

                // 添加 ai 思考信息
                addThinkingMessageToAdapter();

                // ApiClient 的回调函数
                ApiClient.ApiCallback<ChatResponse> chatResponseApiCallback = new ApiClient.ApiCallback<>() {
                    @Override
                    public void onSuccess(ChatResponse result) {
                        String response = result.getResponse();
                        runOnUiThread(() -> {
                            removeThinkingMessageFromAdapter();
                            addMessageToAdapter(response, false, null, filePathToSend, fileMimeTypeToSend);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            removeThinkingMessageFromAdapter();
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            addMessageToAdapter("错误: " + errorMessage, false, null, filePathToSend, fileMimeTypeToSend);
                        });
                    }
                };

                // 调用 LLM API，传入消息列表和缓存的 systemPrompt
                if (hasFile) {
                    Log.d(TAG, "传输的文件路径为 " + filePathToSend);
                    ApiClient.chatWithFile(
                            String.valueOf(currentConversationId),
                            textToSend,
                            systemPrompt,
                            new File(filePathToSend),
                            fileMimeTypeToSend,
                            chatResponseApiCallback
                    );
                } else if (hasImage) {
                    Log.d(TAG, "传输的图片路径为 " + imagePathToSend);
                    ApiClient.chatWithImage(
                            String.valueOf(currentConversationId),
                            textToSend,
                            systemPrompt,
                            new File(imagePathToSend),
                            chatResponseApiCallback
                    );
                } else {
                    ApiClient.chat(
                            String.valueOf(currentConversationId),
                            textToSend,
                            systemPrompt,
                            chatResponseApiCallback
                    );
                }
            }
        });

        imageButton.setOnClickListener(v -> {
            openImagePicker(); // 打开相册即可
            // OPTION 添加相机拍照
        });

        fileButton.setOnClickListener(v -> {
            openFilePicker();
        });

        removeFileButton.setOnClickListener(v -> {
            removeAttachedFile();
        });

        removeImageButton.setOnClickListener(v -> {
            removeAttachedImage();
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

        backButton.setOnClickListener(v -> {
            // 返回上一页
            finish();

        });

        menuButton.setOnClickListener(v -> {
            // 启动 SettingsActivity，并传递当前对话 ID
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            settingsIntent.putExtra("CONVERSATION_ID", currentConversationId);
            startActivityForResult(settingsIntent, REQUEST_SETTINGS); // 使用 startActivityForResult
        });
    }

    // 添加“思考中”消息到适配器
    private void addThinkingMessageToAdapter() {
        thinkingMessage = new Message(null, true);
        Log.d("MainActivity", "Adding thinking message: " + thinkingMessage.getId() + ", isThinking: " + thinkingMessage.isThinking());
        messageList.add(thinkingMessage);
        Log.d("MainActivity", "Message list size after adding thinking: " + messageList.size());
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        messagesRecyclerView.scrollToPosition(messageList.size() - 1);
    }

    // 从适配器移除“思考中”消息
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
    }

    // 打开文件选择器
    private void openFilePicker() {
        // Intent.ACTION_GET_CONTENT 允许用户选择任何类型的内容
        Intent filePickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
        filePickerIntent.addCategory(Intent.CATEGORY_OPENABLE); // 确保选择的文件是可打开的

        // 不打开图片，因为图片被单独处理
        String[] mimeTypes = {"application/msword", "text/plain", "application/pdf"};
        filePickerIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerIntent.setType("application/octet-stream");

        startActivityForResult(Intent.createChooser(filePickerIntent, "选择文件"), REQUEST_FILE_PICK);
    }

    // 显示文件预览（例如，显示文件名）
    private void showAttachedFilePreview(String filePath, String mimeType) {
        // 展示选中文件的文件名
        TextView fileNameTextView = attachedFileContainer.findViewById(R.id.fileNameTextView);
        String fileName = new File(filePath).getName();
        fileNameTextView.setText(fileName);
        // 显示容器
        attachedFileContainer.setVisibility(View.VISIBLE);
    }

    // 移除附件文件
    private void removeAttachedFile() {
        pendingFilePath = null; // 清空路径
        pendingFileMimeType = null; // 清空类型
        // 清空 UI
        attachedFileContainer.setVisibility(View.GONE); // 隐藏容器
    }


    // 在用户输入框上展示用户上传的 图片缩略图
    private void showAttachedImagePreview(String imagePath) {
        // 从 attachedImageViewContainer 中查找 attachedImageView
        ImageView attachedImageView = attachedImageViewContainer.findViewById(R.id.attachedImageView);

        if (attachedImageView != null) { // 👈 添加 null 检查
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap != null) {
                attachedImageView.setImageBitmap(bitmap);
                attachedImageView.setVisibility(View.VISIBLE); // ImageView 本身可见
            } else {
                attachedImageView.setVisibility(View.GONE);
                Toast.makeText(this, "Failed to load image preview.", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            Log.e("MainActivity", "attachedImageView not found within attachedImageViewContainer!");
            Toast.makeText(this, "Failed to load image preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        attachedImageViewContainer.setVisibility(View.VISIBLE); // 显示容器
    }

    // 移除吸附在用户输入框上的 图片缩略图
    private void removeAttachedImage() {
        pendingImagePath = null; // 清空路径

        // 👇 从 attachedImageViewContainer 中查找 attachedImageView
        ImageView attachedImageView = attachedImageViewContainer.findViewById(R.id.attachedImageView);
        if (attachedImageView != null) { // 👈 添加 null 检查
            attachedImageView.setImageBitmap(null); // 清空 ImageView
        }

        attachedImageViewContainer.setVisibility(View.GONE); // 隐藏容器
    }

    // 打开图片选择器(相册或相机，相机功能待实现)
    // OPTION: 打开相机拍照并上传图片
    private void openImagePicker() {
        // 只创建从相册选择图片的 Intent
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // 直接启动选择图片的 Intent
        startActivityForResult(pickImageIntent, REQUEST_IMAGE_PICK);
    }

    // 权限获取
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

    // 获取从另一个页面返回的数据
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String imagePath = null;
            String filePath = null; // 👈 新增
            String fileMimeType = null; // 👈 新增

            if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                try {
                    Uri imageUri = data.getData();
                    // 获取文件输入流
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    if (inputStream != null) {
                        // 将图片复制到应用私有目录
                        imagePath = ImageUtils.copyImageToPrivateStorage(MainActivity.this, inputStream);
                        inputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "处理图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                // 显示图片缩略图
                if (imagePath != null) {
                    // 存储路径
                    pendingImagePath = imagePath;
                    // 显示图片预览
                    showAttachedImagePreview(imagePath);
                }
            } else if (requestCode == REQUEST_SETTINGS && data != null) {
                // 接收从 SettingsActivity 返回的新系统提示词
                String newSystemPrompt = data.getStringExtra("SYSTEM_PROMPT");
                if (newSystemPrompt != null) {
                    // 更新缓存的 systemPrompt
                    systemPrompt = newSystemPrompt;
                    // 可选：显示一个 Toast 提示用户
                    Toast.makeText(this, "系统提示词已更新", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_FILE_PICK && data != null) { // 👈 处理文件选择结果
                try {
                    Uri fileUri = data.getData();
                    if (fileUri != null) {
                        // 获取文件类型
                        fileMimeType = getContentResolver().getType(fileUri);
                        // 获取文件名（可选，用于 UI 显示）
                        String fileName = FileUtils.getFileNameFromUri(MainActivity.this, fileUri);
                        Log.d(TAG, "Selected file: " + fileName + ", Type: " + fileMimeType);

                        // 将文件复制到应用私有目录（可选，取决于你的需求）
                        // 或者直接使用 Uri 获取字节数组
                        byte[] fileBytes = FileUtils.getBytesFromUri(MainActivity.this, fileUri);
                        if (fileBytes != null) {
                            // 将字节数组保存到临时文件（或直接存储在内存/数据库中，取决于大小）
                            String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                            String fileExtension = fileName.substring(fileName.lastIndexOf('.'));
                            String newFileName = fileNameWithoutExt + "_" + System.currentTimeMillis() + fileExtension;
                            File storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS); // 使用 DOCUMENTS 目录
                            if (storageDir != null) {
                                File file = new File(storageDir, newFileName);
                                try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                                    fileOutputStream.write(fileBytes);
                                    fileOutputStream.flush();
                                    filePath = file.getAbsolutePath(); // 获取保存后的路径
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Log.e(TAG, "Error saving file to private storage", e);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "处理文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                if (filePath != null) {
                    pendingFilePath = filePath;
                    pendingFileMimeType = fileMimeType;
                    showAttachedFilePreview(filePath, fileMimeType); // 显示文件预览
                }
            }
        }
    }

    // 将普通消息(可能包含图片)添加至消息列表(用户消息或ai消息)
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

    // 添加消息到适配器 (支持文件)
    private void addMessageToAdapter(String text, boolean isUser, @Nullable String imagePath, @Nullable String filePath, @Nullable String mimeType) {
        // Message 类需要支持 filePath 和 mimeType
        Message message = new Message(text, isUser, imagePath, filePath, mimeType);
        messageList.add(message);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        messagesRecyclerView.scrollToPosition(messageList.size() - 1);

        if (currentConversationId != -1) {
            executorService.execute(() -> {
                // MessageEntity 也需要支持 filePath 和 mimeType
                database.messageDao().insertMessage(
                        new MessageEntity(currentConversationId, text, isUser, imagePath, filePath, mimeType, System.currentTimeMillis())
                );
                if (isNewAndEmpty && isUser) {
                    isNewAndEmpty = false;
                }
            });
        }
    }

    // 创建新的对话
    private void createNewConversation() {
        executorService.execute(() -> {
            ConversationEntity newConversation = new ConversationEntity("新对话", System.currentTimeMillis());
            long newId = database.conversationDao().insertConversation(newConversation);
            currentConversationId = newId;
            isNewAndEmpty = true;
            systemPrompt = ConversationEntity.getDefault_sys_prompt();

            runOnUiThread(() -> {
                messageList.clear();
                messageAdapter.notifyDataSetChanged();
            });
        });
    }

    // 加载本次对话的内容
    private void loadConversation(long id) {
        executorService.execute(() -> {
            ConversationEntity convEntity = database.conversationDao().getConversationById(id);
            List<MessageEntity> messageEntities = database.messageDao().getMessagesForConversation(id);

            List<Message> messages = new ArrayList<>();
            for (MessageEntity entity : messageEntities) {
                messages.add(new Message(entity.getText(), entity.isUser(), entity.getImagePath()));
            }

            final List<Message> finalMessages = messages;
            runOnUiThread(() -> {
                currentConversationId = id;
                // 👇 从 ConversationEntity 获取并设置 systemPrompt
                if (convEntity != null) {
                    systemPrompt = convEntity.getSystemPrompt(); // 缓存到成员变量
                } else {
                    systemPrompt = null; // 或者设置一个默认值
                }
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

    @Override
    protected void onResume() {
        super.onResume();
        // 👇 当 Activity 重新可见时，检查是否需要重新加载当前对话
        // 例如，用户可能在 SettingsActivity 中清除了对话
        if (currentConversationId != -1) {
            // 如果当前有对话 ID，尝试重新加载该对话
            // 这会覆盖当前的 messageList，但如果对话被清空，messageList 将变为新的空列表
            // 如果对话被删除，getConversationById 可能返回 null，需要处理这种情况
            loadConversation(currentConversationId);
        } else {
            // 如果 currentConversationId 是 -1，说明这是一个新对话或刚刚创建
            // 并且尚未发送任何消息（因为发送消息后 ID 会被设置）
            // 在 createNewConversation 成功后，ID 会被设置，下次 onResume 会尝试加载
            // 对于一个全新的、空的对话，messageList 本来就是空的，无需特殊处理
        }
    }
}