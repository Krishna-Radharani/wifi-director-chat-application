package com.example.wifidirectchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    public interface OnMessageLongClickListener {
        void onMessageLongClick(String messageText);
    }

    private List<Message> messageList;
    private OnMessageLongClickListener longClickListener;

    public MessageAdapter(List<Message> messageList, OnMessageLongClickListener longClickListener) {
        this.messageList = messageList;
        this.longClickListener = longClickListener;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        Message message = messageList.get(position);
        holder.textView.setText(message.getMessageText());

        // Change background for sender/receiver
        if (message.isSender()) {
            holder.textView.setBackgroundResource(R.drawable.bubble_sender);
        } else {
            holder.textView.setBackgroundResource(R.drawable.bubble_receiver);
        }

        // Long click to trigger reply
        holder.textView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message.getMessageText());
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        MessageViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.textViewMessage);
        }
    }
}
