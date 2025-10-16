package com.dyk.new_app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class ImageUtils {
    private static final String TAG = "ImageUtils";
    public static String encodeImageToBase64(String imagePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode bitmap from path: " + imagePath);
            return null;
        }
        // 可选：压缩图片以减少 Base64 长度和 API 负载
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream); // 压缩示例
        //bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream); // 不压缩
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP); // 使用 NO_WRAP 以避免换行符
    }
}
