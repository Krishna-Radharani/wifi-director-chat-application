package com.example.wifidirectchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_RECEIVED = 0;
    private static final int TYPE_SENT = 1;

    private List<Message> messageList;

    public MessageAdapter(List<Message> messages) {
        this.messageList = messages;
    }

    // Determine view type based on whether message was sent or received
    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isSent() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // Create ViewHolder based on view type
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(view);
        }
    }

    // Bind data to the ViewHolder
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Message msg = messageList.get(position);
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).tvBody.setText(msg.getText());
        } else if (holder instanceof ReceivedViewHolder) {
            ((ReceivedViewHolder) holder).tvBody.setText(msg.getText());
        }
    }

    // ViewHolder for sent messages
    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView tvBody;
        SentViewHolder(View itemView) {
            super(itemView);
            tvBody = itemView.findViewById(R.id.tv_message_body);
        }
    }

    // ViewHolder for received messages
    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView tvBody;
        ReceivedViewHolder(View itemView) {
            super(itemView);
            tvBody = itemView.findViewById(R.id.tv_message_body);
        }
    }
}
