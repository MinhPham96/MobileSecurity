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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.myViewHolder>  {
    private List<Device> mDataset = new ArrayList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private static final String deviceCollection = "devices" ;
    private FirebaseFirestore mFirestore;
    private DocumentReference alertDocRef;
    private boolean initCheck = false;


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
        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", mDataset.get(position).getMacAddress())
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()) {
                    for(DocumentSnapshot documentSnapshot : task.getResult().getDocuments()) {
                        //set the path for the device document
                        alertDocRef = mFirestore.collection(deviceCollection).document(documentSnapshot.getId());
                        //since the listener only works with ths setup
                        //put the function outside here will not work
                        setAlertListener(holder.deviceAlertDateTextView);
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

    public void setAlertListener(final TextView newAlertTextView) {
        //check this document if there is any update in event
        alertDocRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@javax.annotation.Nullable DocumentSnapshot documentSnapshot,
                                @javax.annotation.Nullable FirebaseFirestoreException e) {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            //use the init check to make sure the old event is not counted as new alert
            if (documentSnapshot != null && documentSnapshot.exists()) {
                if(initCheck) {
                    newAlertTextView.setText(dateFormat.format(documentSnapshot.toObject(Device.class).getAlert().getDate()));
                }
                initCheck = true;
                Log.i(TAG, "Current data: " + documentSnapshot.getData());
            } else {
                Log.i(TAG, "Current data: null");
                initCheck = true;
            }
            }
        });
    }
}
