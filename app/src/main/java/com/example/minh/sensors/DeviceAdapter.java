package com.example.minh.sensors;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.myViewHolder>  {
    private List<Device> mDataset = new ArrayList<>();
    private HashMap<String, String> mac_id_hashmap;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private static final String TAG = "Main Activity";

    private String deviceCollection;
    private String userCollection;
    private String historyCollection;
    private String sharedDeviceId;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser user;
    private Context context;
    private SharedPreferences sharedPref;

    //custom view holder class to store all the element of the row
    public static class myViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView, deviceAlertDateTextView;
        Button removeButton;
        public myViewHolder(View itemView) {
            super(itemView);

            deviceNameTextView = (TextView) itemView.findViewById(R.id.deviceNameTextView);
            deviceAlertDateTextView = (TextView) itemView.findViewById(R.id.deviceAlertDateTextView);
            removeButton = (Button) itemView.findViewById(R.id.deviceRemoveButton);
        }
    }

    public DeviceAdapter(List<Device> myDataset, HashMap<String, String> hm, Context context) {
        this.mDataset = myDataset;
        this.mac_id_hashmap = hm;
        this.context = context;
    }

    @NonNull
    @Override
    public myViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mFirestore = FirebaseFirestore.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        user = mFirebaseAuth.getCurrentUser();
        userCollection = context.getResources().getString(R.string.fireStoreUserCollection);
        deviceCollection = context.getResources().getString(R.string.fireStoreDeviceCollection);
        historyCollection = context.getResources().getString(R.string.fireStoreHistoryCollection);
        sharedDeviceId = context.getResources().getString(R.string.sharedPrefDeviceId);
        sharedPref = context.getSharedPreferences("com.example.app", Context.MODE_PRIVATE);

        // create a new view, get the list item layout
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.device_item, parent, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(lp);
        return new myViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final myViewHolder holder, final int position) {

        //get the device ID from the hashmap using the MAC
        final String deviceId = mac_id_hashmap.get(mDataset.get(position).getMacAddress());
        //get activity from context
        Activity activity = (Activity) context;
        //set the output for the item row
        //set a listener to the document in the device root collection that have the same MAC
        //get the document that has the same MAC address in the root device collection
        //check if there is any update
        //if yes, alert user
        //add activity to snapshot listener to automatically remove the listener when the activity stop
        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", mDataset.get(position).getMacAddress())
                .addSnapshotListener(activity ,new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                Log.i(TAG, "get snapshot");
                if(e != null) {
                    return;
                }
                for(DocumentChange documentChange: queryDocumentSnapshots.getDocumentChanges()) {
                    switch (documentChange.getType()) {
                        case MODIFIED:
                            for(DocumentSnapshot documentSnapshot: queryDocumentSnapshots.getDocuments()) {
                                //get the alert from the snapshot
                                Alert alert = documentSnapshot.toObject(Device.class).getAlert();
                                //display the date on the item row
                                holder.deviceAlertDateTextView.setText(
                                        dateFormat.format(alert.getDate()));
                                //add the alert to the history collection of the user device document
                                mFirestore.collection(userCollection).document(user.getUid())
                                        .collection(deviceCollection).document(deviceId)
                                        .collection(historyCollection).add(alert);
                            }
                            break;
                    }
                }
                }
            });

        holder.deviceNameTextView.setText(mDataset.get(position).getName());

        //remove device button
        holder.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //get the device ID from the hashmap
                Device device = mDataset.get(position);
                device.setOwned(false);
                if(user != null) {
                    mFirestore.collection(userCollection).document(user.getUid())
                            .collection(deviceCollection).document(deviceId).set(device);
                    mDataset.remove(position);
                    notifyItemRemoved(position);
                    notifyItemChanged(position, mDataset.size());
                }
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, HistoryActivity.class);
                context.startActivity(intent);
                sharedPref.edit().putString(sharedDeviceId, deviceId).apply();
            }
        });
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
