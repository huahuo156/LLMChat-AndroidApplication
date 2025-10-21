package com.dyk.new_app.llm_chat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final String BASE_URL = "http://192.168.35.217:5000/llm_chat";
    private static final OkHttpClient client;
    private static final Gson gson = new GsonBuilder().create(); // 创建Gson实例
    private static final Handler mainHandler = new Handler(Looper.getMainLooper()); // 用于切换到主线程

    static {
        // 配置OkHttpClient
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // 设置连接超时
                .readTimeout(60, TimeUnit.SECONDS)    // 设置读取超时
                .writeTimeout(60, TimeUnit.SECONDS)   // 设置写入超时
                .build();
    }

    // --- 接口回调接口定义 ---
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    // --- 普通对话接口 ---
    public static void chat(String session_id,String userMessage, String system_prompt,ApiCallback<ChatResponse> callback) {
        ChatRequest requestObj = new ChatRequest(session_id,userMessage,system_prompt);
        String jsonBody = gson.toJson(requestObj);

        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/chat")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Chat request failed: ", e);
                runOnMainThread(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Chat request failed with code: " + response.code() + ", message: " + response.message());
                        runOnMainThread(() -> callback.onError("Server error: " + response.code() + " - " + response.message()));
                        return;
                    }
                    String responseBody = response.body().string();
                    Log.d(TAG, "Chat response body: " + responseBody);
                    ChatResponse chatResponse = gson.fromJson(responseBody, ChatResponse.class);
                    runOnMainThread(() -> callback.onSuccess(chatResponse));
                } finally {
                    response.close();
                }
            }
        });
    }

    // --- 图片对话接口 ---
    public static void chatWithImage(String session_id,String userMessage,String system_prompt, File imageFile, ApiCallback<ChatResponse> callback) {
        if (imageFile == null || !imageFile.exists()) {
            Log.e(TAG, "Image file is null or does not exist.");
            runOnMainThread(() -> callback.onError("Image file is invalid."));
            return;
        }

        RequestBody imageBody = RequestBody.create(imageFile, MediaType.parse("image/*"));

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message", userMessage)
                .addFormDataPart("session_id", session_id)
                .addFormDataPart("system_prompt", system_prompt)
                .addFormDataPart("image", imageFile.getName(), imageBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat_with_image")
                .post(multipartBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Chat with image request failed: ", e);
                runOnMainThread(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Chat with image request failed with code: " + response.code() + ", message: " + response.message());
                        runOnMainThread(() -> callback.onError("Server error: " + response.code() + " - " + response.message()));
                        return;
                    }
                    String responseBody = response.body().string();
                    Log.d(TAG, "Chat with image response body: " + responseBody);
                    ChatResponse chatResponse = gson.fromJson(responseBody, ChatResponse.class);
                    runOnMainThread(() -> callback.onSuccess(chatResponse));
                } finally {
                    response.close();
                }
            }
        });
    }

    // --- 文件对话接口 ---
    public static void chatWithFile(String session_id,String userMessage,String system_prompt, File file,String mimeType ,ApiCallback<ChatResponse> callback) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "File is null or does not exist.");
            runOnMainThread(() -> callback.onError("File is invalid."));
            return;
        }

        RequestBody messageBody = RequestBody.create(userMessage, MediaType.parse("text/plain"));
        RequestBody fileBody = RequestBody.create(file, MediaType.parse(mimeType));

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message", userMessage)
                .addFormDataPart("session_id", session_id)
                .addFormDataPart("system_prompt", system_prompt)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat_with_file")
                .post(multipartBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Chat with file request failed: ", e);
                runOnMainThread(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Chat with file request failed with code: " + response.code() + ", message: " + response.message());
                        runOnMainThread(() -> callback.onError("Server error: " + response.code() + " - " + response.message()));
                        return;
                    }
                    String responseBody = response.body().string();
                    Log.d(TAG, "Chat with file response body: " + responseBody);
                    ChatResponse chatResponse = gson.fromJson(responseBody, ChatResponse.class);
                    runOnMainThread(() -> callback.onSuccess(chatResponse));
                } finally {
                    response.close();
                }
            }
        });
    }

    // --- 文本转语音接口 ---
    public static void textToSpeech(String text, String outputFilePath, ApiCallback<String> callback) { // 返回文件保存路径或错误信息
        TtsRequest requestObj = new TtsRequest(text);
        String jsonBody = gson.toJson(requestObj);

        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/tts")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "TTS request failed: ", e);
                runOnMainThread(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "TTS request failed with code: " + response.code() + ", message: " + response.message());
                        runOnMainThread(() -> callback.onError("Server error: " + response.code() + " - " + response.message()));
                        return;
                    }

                    // 假设后端返回的是音频字节流
                    byte[] audioBytes = response.body().bytes();

                    // 尝试保存到指定路径
                    try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                        fos.write(audioBytes);
                        Log.d(TAG, "Audio saved successfully to: " + outputFilePath);
                        runOnMainThread(() -> callback.onSuccess(outputFilePath)); // 返回保存路径
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to save audio file: ", e);
                        runOnMainThread(() -> callback.onError("Failed to save audio file: " + e.getMessage()));
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    // --- 健康检查接口 ---
    public static void healthCheck(ApiCallback<String> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/health")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Health check request failed: ", e);
                runOnMainThread(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Health check request failed with code: " + response.code() + ", message: " + response.message());
                        runOnMainThread(() -> callback.onError("Server error: " + response.code() + " - " + response.message()));
                        return;
                    }
                    String responseBody = response.body().string();
                    Log.d(TAG, "Health check response: " + responseBody);
                    // 假设健康检查成功返回一个简单的字符串
                    runOnMainThread(() -> callback.onSuccess(responseBody));
                } finally {
                    response.close();
                }
            }
        });
    }

    // --- 辅助方法：在主线程执行回调 ---
    private static void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
}