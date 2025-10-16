package com.dyk.new_app.llm_chat;

import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.dyk.new_app.entity.Message;
import com.dyk.new_app.util.ImageUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LLMApiClient {

    private static final String TAG = "LLMApiClient";
    private  String LLM_API_URL = "https://api.deepseek.com/chat/completions";
    private  String LLM_API_KEY = "sk-a5c1121f490845e6a5468540cfb7cadb";
    private static final int TIMEOUT_SECONDS = 120;

    private final OkHttpClient httpClient;
    private final ExecutorService executorService;

    public interface LLMCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    public void setLLM_API_URL(String LLM_API_URL){
        this.LLM_API_URL = LLM_API_URL.trim();
    }

    public void setLLM_API_KEY(String LLM_API_KEY){
        this.LLM_API_KEY = LLM_API_KEY.trim();
    }

    public LLMApiClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.executorService = Executors.newFixedThreadPool(2);
    }

    public void sendMessage(List<Message> contextMessages ,LLMCallback callback) {
        // 1. 构建请求体
        JSONObject requestBodyJson = new JSONObject();
        try {
            requestBodyJson.put("model", "deepseek-chat");
            // 移除 "stream": true
            // requestBodyJson.put("stream", true);

            JSONArray messagesArray = new JSONArray();
            // 添加上下文
            for (Message msg : contextMessages) {

                // 跳过无实际意义的信息
                if(msg.isThinking())continue;

                JSONObject messageJson = new JSONObject();
                messageJson.put("role", msg.isUser() ? "user" : "assistant");

                if (msg.isUser()) {
                    // 用户消息：可能包含文本和图片，使用数组格式
                    JSONArray contentArray = new JSONArray();
                    // 添加文本内容
                    if (!TextUtils.isEmpty(msg.getText())) {
                        JSONObject textContent = new JSONObject();
                        textContent.put("type", "text");
                        textContent.put("text", msg.getText());
                        contentArray.put(textContent);
                    }

                    // 添加图片内容 (仅限用户消息)
                    if (msg.getImagePath() != null) {
                        // 注意：这里需要 MainActivity 中的 encodeImageToBase64 逻辑
                        // 最好的方式是在 MainActivity 调用前处理图片编码
                        // 或者创建一个独立的工具类
                        String base64Image = encodeImageToBase64(msg.getImagePath()); // 假设这是一个静态方法或传递编码结果
                        if (base64Image != null) {
                            JSONObject imageContent = new JSONObject();
                            imageContent.put("type", "image_url");
                            imageContent.put("image_url", new JSONObject().put("url", "image/jpeg;base64," + base64Image));
                            contentArray.put(imageContent);
                        } else {
                            Log.e(TAG, "Failed to encode image to Base64: " + msg.getImagePath());
                            Log.w(TAG, "Skipping image for message due to encoding failure.");
                        }
                    }
                    messageJson.put("content", contentArray);
                } else {
                    // AI 消息：通常只包含文本，使用字符串格式
                    messageJson.put("content", msg.getText() != null ? msg.getText() : "");
                }

                messagesArray.put(messageJson);
            }

            requestBodyJson.put("messages", messagesArray);
            requestBodyJson.put("max_tokens", 2048); // 根据需要调整

        } catch (JSONException e) {
            Log.e(TAG, "Error building request JSON", e);
            // 在主线程上执行回调
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onError("构建请求失败: " + e.getMessage()));
            return; // 结束方法
        }

        // 2. 创建 Request 对象
        RequestBody body = RequestBody.create(requestBodyJson.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(LLM_API_URL.trim())
                .post(body)
                .addHeader("Authorization", "Bearer " + LLM_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        Log.d(TAG, "Sending request to LLM API: " + requestBodyJson.toString());

        // 3. 异步发送请求
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "LLM API request failed", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onError("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // 注意：onResponse 在子线程执行
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No body";
                    Log.e(TAG, "LLM API request failed with code: " + response.code() + ", message: " + response.message());
                    Log.e(TAG, "Response body: " + errorBody);
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onError("API 请求失败: " + response.code() + " " + response.message() + "\n" + errorBody));
                    return; // 退出回调
                }

                if (response.body() == null) {
                    Log.e(TAG, "LLM API response body is null");
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onError("API 响应体为空"));
                    return; // 退出回调
                }

                try {
                    String responseBody = response.body().string();
                    Log.d(TAG, "LLM API response: " + responseBody);
                    // 4. 解析响应
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray choicesArray = jsonResponse.getJSONArray("choices");
                    if (choicesArray.length() > 0) {
                        JSONObject choice = choicesArray.getJSONObject(0);
                        JSONObject messageObj = choice.getJSONObject("message");
                        String llmResponseText = messageObj.getString("content").trim();

                        // 5. 在主线程返回成功结果
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> callback.onSuccess(llmResponseText));
                    } else {
                        Log.e(TAG, "LLM API response has no choices");
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> callback.onError("API 响应格式错误"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body", e);
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onError("读取响应失败: " + e.getMessage()));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing LLM API response JSON", e);
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onError("解析响应失败: " + e.getMessage()));
                }
            }
        });
    }


    private String encodeImageToBase64(String imagePath) {
        Log.w(TAG, "encodeImageToBase64 not implemented in LLMApiClient. Image path: " + imagePath);
        // 为了当前示例，返回 null 表示无法编码
        return ImageUtils.encodeImageToBase64(imagePath);
    }
}