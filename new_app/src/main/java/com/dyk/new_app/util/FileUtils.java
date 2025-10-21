package com.dyk.new_app.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {

    private static final String TAG = "FileUtils";

    // 将 Uri 转换为字节数组 (用于 API 上传)
    public static byte[] getBytesFromUri(Context context,Uri uri) {
        byte[] bytes = null;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (inputStream != null) {
                int nRead;
                byte[] data = new byte[1024]; // 或其他合适的缓冲区大小
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                bytes = buffer.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error reading file from URI: " + uri, e);
        }
        return bytes;
    }

    // 从文件的 uri 获取文件名
    public static String getFileNameFromUri(Context context,Uri uri) {
        String fileName = "unknown_file";
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        } else if (uri.getScheme().equals("file")) {
            fileName = new File(uri.getPath()).getName();
        }
        return fileName;
    }
}
