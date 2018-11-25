package com.example.minh.sensors;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.myViewHolder> {
    private List<Alert> mDataset = new ArrayList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private Context context;

    //custom view holder class to store all the element of the row
    public static class myViewHolder extends RecyclerView.ViewHolder {
        TextView alertDateTextView;
        ImageView alertImageView;
        ImageButton alertArrowButton;
        boolean showImage = false;
        public myViewHolder(View itemView) {
            super(itemView);
            alertDateTextView = (TextView) itemView.findViewById(R.id.alertDateTextView);
            alertImageView = (ImageView) itemView.findViewById(R.id.alertImageView);
            alertArrowButton = (ImageButton) itemView.findViewById(R.id.alertArrowButton);
        }
    }

    public AlertAdapter(List<Alert> myDataset, Context context) {
        this.mDataset = myDataset;
        this.context = context;
    }

    @NonNull
    @Override
    public myViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view, get the list item layout
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.alert_item, parent, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(lp);
        return new myViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final myViewHolder holder, int position) {
        //set the output for the item row
        holder.alertDateTextView.setText(dateFormat.format(mDataset.get(position).getDate()));
        if(mDataset.get(position).getImageURL() != null) {
            Glide.with(context)
                    .load(mDataset.get(position).getImageURL())
                    .into(holder.alertImageView);
        }
        holder.alertArrowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(holder.showImage) {
                    holder.showImage = false;
                    holder.alertImageView.setVisibility(View.GONE);
                    holder.alertArrowButton.setImageResource(R.drawable.down);
                } else {
                    holder.showImage = true;
                    holder.alertImageView.setVisibility(View.VISIBLE);
                    holder.alertArrowButton.setImageResource(R.drawable.up);
                }
            }
        });
    }

    //get the size of the list
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
