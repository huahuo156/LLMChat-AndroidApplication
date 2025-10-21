package com.dyk.new_app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    public static String copyImageToPrivateStorage(Context context, InputStream inputStream) {
        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES); // 获取应用私有图片目录

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
}
