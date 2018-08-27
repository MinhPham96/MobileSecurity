package com.example.minh.sensors;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import static android.content.ContentValues.TAG;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.myViewHolder>  {
    private List<Device> mDataset = new ArrayList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private static final String deviceCollection = "devices" ;
    private FirebaseFirestore mFirestore;


    //custom view holder class to store all the element of the row
    public static class myViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView, deviceAlertDateTextView;
        public myViewHolder(View itemView) {
            super(itemView);

            deviceNameTextView = (TextView) itemView.findViewById(R.id.deviceNameTextView);
            deviceAlertDateTextView = (TextView) itemView.findViewById(R.id.deviceAlertDateTextView);
        }
    }

    public DeviceAdapter(List<Device> myDataset) {
        this.mDataset = myDataset;
    }

    @NonNull
    @Override
    public myViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view, get the list item layout
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.device_item, parent, false);
        return new myViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final myViewHolder holder, int position) {
        //set the output for the item row
        //set a listener to the document in the device root collection that have the same MAC
        mFirestore = FirebaseFirestore.getInstance();
        //get the document that has the same MAC address in the root device collection
        //check if there is any update
        //if yes, alert user
        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", mDataset.get(position).getMacAddress())
            .addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                for(DocumentChange documentChange: queryDocumentSnapshots.getDocumentChanges()) {
                    switch (documentChange.getType()) {
                        case MODIFIED:
                            for(DocumentSnapshot documentSnapshot: queryDocumentSnapshots.getDocuments()) {
                                holder.deviceAlertDateTextView.setText(
                                        dateFormat.format(documentSnapshot.toObject(Device.class).getAlert().getDate()));
                            }
                            break;
                    }
                }
                }
            });

        holder.deviceNameTextView.setText(mDataset.get(position).getName());
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

}
