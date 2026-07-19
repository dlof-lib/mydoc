package com.dlofpkg.massage.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dlofpkg.massage.R;
import com.dlofpkg.massage.model.Post;
import com.dlofpkg.massage.ui.PostDetailActivity;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private final List<Post> posts = new ArrayList<>();

    public void setPosts(List<Post> newPosts) {
        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.tvTitle.setText(post.getTitle());
        holder.tvAuthor.setText("بواسطة: " + post.getAuthorUsername());

        String typeLabel;
        switch (post.getType()) {
            case Post.TYPE_CODE:
                typeLabel = "ملف كود";
                break;
            case Post.TYPE_ARTICLE:
                typeLabel = "مقال";
                break;
            default:
                typeLabel = "نص";
        }
        holder.tvType.setText(typeLabel);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), PostDetailActivity.class);
            intent.putExtra("post_id", post.getId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAuthor, tvType;

        PostViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvType = itemView.findViewById(R.id.tvType);
        }
    }
}
