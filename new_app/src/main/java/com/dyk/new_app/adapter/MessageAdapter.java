package com.dyk.new_app.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dyk.new_app.R;
import com.dyk.new_app.entity.Message;

import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;
    private static final int VIEW_TYPE_THINKING = 2;
    private final List<Message> messageList;
    private final Markwon markwon; // Markwon 实例

    public MessageAdapter(List<Message> messageList, Context context) {
        this.messageList = messageList;
        setHasStableIds(true);

        // 创建 Markwon 实例，可以添加插件以支持更多功能
        this.markwon = Markwon.builder(context)
                .usePlugin(LinkifyPlugin.create()) // 自动识别链接 (可选)
                .usePlugin(StrikethroughPlugin.create()) // 支持删除线 ~~~~
                .usePlugin(TablePlugin.create(context)) // 支持表格 (需要添加 TablePlugin 依赖)
                .build();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        // 👇 根据 ViewType 选择不同的布局文件
        if (viewType == VIEW_TYPE_USER) {
            // 你当前的 item_message 布局可能主要包含用户消息的 UI
            // 如果用户和 AI 消息 UI 差异较大，建议拆分布局
            // 这里暂时假设 item_message 包含了所有必要的 UI 组件 (userLayout, aiLayout, thinkingLayout)
            // 或者你可能需要创建 item_message_user.xml 和 item_message_ai.xml
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        } else if (viewType == VIEW_TYPE_AI) {
            // 同上，如果布局差异大，使用独立的 item_message_ai.xml
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        } else { // VIEW_TYPE_THINKING
            // 创建一个专门用于“AI 正在思考”的布局文件
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_thinking, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        if (message.isThinking()) {
            return VIEW_TYPE_THINKING;
        } else if (message.isUser()) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_AI;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);
        int viewType = getItemViewType(position);

        Log.d("MessageAdapter", "onBindViewHolder - Position: " + position + ", Message ID: " + message.getId() + ", isThinking: " + message.isThinking());

        // 👇 总是重置所有可能存在的视图状态，防止复用时残留
        // 使用 null 检查来安全地调用方法
        if (holder.userMessageLayout != null) holder.userMessageLayout.setVisibility(View.GONE);
        if (holder.aiMessageLayout != null) holder.aiMessageLayout.setVisibility(View.GONE);
        if (holder.thinkingLayout != null) holder.thinkingLayout.setVisibility(View.GONE);
        if (holder.thinkingProgressBar != null) holder.thinkingProgressBar.setVisibility(View.GONE);

        if (holder.userMessageTextView != null) holder.userMessageTextView.setText("");
        if (holder.aiMessageTextView != null) holder.aiMessageTextView.setText("");

        if (holder.userMessageImageView != null) {
            holder.userMessageImageView.setVisibility(View.GONE);
            holder.userMessageImageView.setImageBitmap(null);
        }

        // 👇 根据消息类型设置 UI
        if (viewType == VIEW_TYPE_USER) {
            // 设置用户消息布局可见
            if (holder.userMessageLayout != null) holder.userMessageLayout.setVisibility(View.VISIBLE);
            if (holder.userMessageTextView != null) holder.userMessageTextView.setText(message.getText());

            if (holder.userMessageImageView != null) { // 检查 ImageView 是否存在
                String imagePath = message.getImagePath();
                if (imagePath != null && !imagePath.isEmpty()) {
                    Bitmap bitmap = loadBitmapFromFile(imagePath, holder.userMessageImageView);
                    if (bitmap != null) {
                        holder.userMessageImageView.setImageBitmap(bitmap);
                        holder.userMessageImageView.setVisibility(View.VISIBLE);
                    } else {
                        holder.userMessageImageView.setVisibility(View.GONE);
                        Log.e("MessageAdapter", "Failed to load bitmap from path for user message at position: " + position + ", Path: " + imagePath);
                    }
                } else {
                    holder.userMessageImageView.setVisibility(View.GONE);
                }
            }
        } else if (viewType == VIEW_TYPE_AI) {
            // 设置 AI 消息布局可见
            if (holder.aiMessageLayout != null) holder.aiMessageLayout.setVisibility(View.VISIBLE);
            if (holder.aiMessageTextView != null) { // 检查 TextView 是否存在
                String aiText = message.getText();
                if (!aiText.isEmpty()) {
                    markwon.setMarkdown(holder.aiMessageTextView, aiText);
                } else {
                    holder.aiMessageTextView.setText("");
                }
            }
        } else { // VIEW_TYPE_THINKING
            Log.d("MessageAdapter", "Binding thinking message at position: " + position);
            // 设置“思考中”布局可见
            if (holder.thinkingLayout != null) holder.thinkingLayout.setVisibility(View.VISIBLE);
            if (holder.thinkingProgressBar != null) {
                holder.thinkingProgressBar.setVisibility(View.VISIBLE);
                holder.thinkingProgressBar.setIndeterminate(true);
            }
            // 如果 thinking layout 里有 TextView，也可以设置文字 (需要先检查)
            // if (holder.thinkingTextView != null) {
            //     holder.thinkingTextView.setText(message.getText()); // "AI 正在思考..."
            // }
        }
    }

    // 👇从文件路径加载并缩放 Bitmap
    private Bitmap loadBitmapFromFile(String imagePath, ImageView imageView) {
        try {
            // 1. 获取 ImageView 的目标尺寸（或设置一个合理的默认值）
            // 注意：在 onBindViewHolder 被调用时，View 可能尚未完全布局，getWidth/Height 可能为 0。
            // 更好的做法是设置一个固定的期望最大尺寸，或者使用 ViewTreeObserver.OnGlobalLayoutListener 等待布局完成。
            // 这里使用一个示例值，您可能需要根据实际布局调整。
            int targetWidth = imageView.getMaxWidth() > 0 ? imageView.getMaxWidth() : 400; // 例如 400px
            int targetHeight = imageView.getMaxHeight() > 0 ? imageView.getMaxHeight() : 400; // 例如 400px

            // 2. 使用 inJustDecodeBounds 获取图片原始尺寸，不加载到内存
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            // 3. 计算 inSampleSize
            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);

            // 4. 禁用 inJustDecodeBounds，实际加载缩放后的图片
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(imagePath, options);

        } catch (Exception e) {
            Log.e("MessageAdapter", "Error loading bitmap from file: " + imagePath, e);
            return null; // 加载失败返回 null
        }
    }

    // 👇 计算采样大小的辅助方法 (与 MainActivity 中类似)
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout userMessageLayout, aiMessageLayout;
        TextView userMessageTextView, aiMessageTextView;
        ImageView userMessageImageView;
        LinearLayout thinkingLayout; // “思考中”布局的根元素
        ProgressBar thinkingProgressBar; // “思考中”的进度条
        TextView thinkingTextView;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);

            userMessageLayout = itemView.findViewById(R.id.userMessageLayout); // 在 item_message_thinking 中为 null
            userMessageTextView = itemView.findViewById(R.id.userMessageTextView); // 在 item_message_thinking 中为 null
            userMessageImageView = itemView.findViewById(R.id.userMessageImageView); // 在 item_message_thinking 中为 null

            aiMessageLayout = itemView.findViewById(R.id.aiMessageLayout); // 在 item_message_thinking 中为 null
            aiMessageTextView = itemView.findViewById(R.id.aiMessageTextView); // 在 item_message_thinking 中为 null

            thinkingLayout = itemView.findViewById(R.id.thinkingLayout); // 在 item_message 中为 null
            thinkingProgressBar = itemView.findViewById(R.id.thinkingProgressBar); // 在 item_message 中为 null
            thinkingTextView = itemView.findViewById(R.id.thinkingTextView);
        }
    }

    @Override
    public long getItemId(int position) {
        Message message = messageList.get(position);
        // 使用 Message 的唯一 ID 的 hashCode 作为 RecyclerView 的 stable ID
        // 这样即使 position 变化，只要 Message 对象不变，ID 就不变
        return message.getId().hashCode(); // 这是一个 int，会被自动提升为 long
    }
}
