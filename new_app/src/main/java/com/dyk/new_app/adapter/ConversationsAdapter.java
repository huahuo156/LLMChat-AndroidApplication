package com.dyk.new_app.adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dyk.new_app.MainActivity;
import com.dyk.new_app.R;
import com.dyk.new_app.database.AppDatabase;
import com.dyk.new_app.entity.ConversationEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder> {

    private final List<ConversationEntity> conversationList;
    private final Context context;
    private final ExecutorService executorService; // 用于数据库操作
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

    public ConversationsAdapter(List<ConversationEntity> conversationList, Context context) {
        this.conversationList = conversationList;
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationEntity conversation = conversationList.get(position);
        holder.titleTextView.setText(conversation.getTitle());
        holder.timestampTextView.setText(dateFormat.format(new Date(conversation.getTimestamp())));

        // 设置点击事件，跳转到 MainActivity 并传递 conversation ID
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("CONVERSATION_ID", conversation.getId());
            context.startActivity(intent);
        });

        // 👇 设置长按事件
        holder.itemView.setOnLongClickListener(v -> {
            // 弹出确认对话框（可选，这里用 Toast 简单提示）
            Toast.makeText(context, "成功删除对话" + conversation.getTitle(), Toast.LENGTH_SHORT).show();

            // 在后台线程删除数据库中的对话
            executorService.execute(() -> {
                AppDatabase database = AppDatabase.getDatabase(context.getApplicationContext());
                try {

                    long conversationIdToDelete = conversation.getId();

                    // 👇 首先删除 messages 表中属于该对话的所有消息
                    database.messageDao().deleteMessagesForConversation(conversationIdToDelete); // 需要在 MessageDao 中添加此方法

                    // 👇 然后删除 conversations 表中的对话
                    database.conversationDao().deleteConversationById(conversationIdToDelete);

                    // 在主线程更新 UI
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        // 从列表中移除该项
                        int adapterPosition = holder.getAdapterPosition(); // 获取当前 holder 的位置
                        if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < conversationList.size()) {
                            conversationList.remove(adapterPosition);
                            // 通知适配器数据已移除
                            notifyItemRemoved(adapterPosition);
                            Toast.makeText(context, "对话已删除", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.w("ConversationsAdapter", "Position might have changed, skipping UI update for deletion.");
                            // 如果 position 无效，可能是因为列表已经更新，跳过 UI 更新
                        }
                    });
                } catch (Exception e) {
                    Log.e("ConversationsAdapter", "Error deleting conversation: ", e);
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "删除对话失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
            return true; // 返回 true 表示消费了长按事件
        });
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView timestampTextView;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.conversationTitleTextView);
            timestampTextView = itemView.findViewById(R.id.conversationTimestampTextView);
        }
    }
}
