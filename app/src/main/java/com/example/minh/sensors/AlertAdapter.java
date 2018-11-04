package com.example.minh.sensors;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.myViewHolder> {
    private List<Alert> mDataset = new ArrayList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    //custom view holder class to store all the element of the row
    public static class myViewHolder extends RecyclerView.ViewHolder {
        TextView alertDateTextView;
        public myViewHolder(View itemView) {
            super(itemView);
            alertDateTextView = (TextView) itemView.findViewById(R.id.alertDateTextView);
        }
    }

    public AlertAdapter(List<Alert> myDataset) {
        this.mDataset = myDataset;
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
    public void onBindViewHolder(@NonNull myViewHolder holder, int position) {
        //set the output for the item row
        holder.alertDateTextView.setText(dateFormat.format(mDataset.get(position).getDate()));
    }

    //get the size of the list
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
