package com.example.minh.sensors;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.myViewHolder>  {
    private List<Device> mDataset = new ArrayList<>();
    private HashMap<String, String> mac_id_hashmap;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final String TAG = "Main Activity";

    private String deviceCollection;
    private String userCollection;
    private String historyCollection;
    private String sharedDeviceId;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private List<ListenerRegistration> listenerRegistrationList = new ArrayList<>();
    private FirebaseUser user;
    private Context context;
    private SharedPreferences sharedPref;

    private static final String CHANNEL_ID  = "MS1211";

    //custom view holder class to store all the element of the row
    public static class myViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView, deviceAlertDateTextView;
        ImageButton removeButton, editButton, arrowButton;
        ImageView deviceImageView;
        boolean showImage = false;
        public myViewHolder(View itemView) {
            super(itemView);
            deviceNameTextView = (TextView) itemView.findViewById(R.id.deviceNameTextView);
            deviceAlertDateTextView = (TextView) itemView.findViewById(R.id.deviceAlertDateTextView);
            removeButton = (ImageButton) itemView.findViewById(R.id.deviceRemoveButton);
            editButton = (ImageButton) itemView.findViewById(R.id.deviceEditButton);
            arrowButton = (ImageButton) itemView.findViewById(R.id.deviceArrowButton);
            deviceImageView = (ImageView) itemView.findViewById(R.id.deviceImageView);
        }
    }

    public DeviceAdapter(List<Device> myDataset, HashMap<String, String> hm, Context context) {
        this.mDataset = myDataset;
        this.mac_id_hashmap = hm;
        this.context = context;

        mFirestore = FirebaseFirestore.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        user = mFirebaseAuth.getCurrentUser();
        userCollection = context.getResources().getString(R.string.fireStoreUserCollection);
        deviceCollection = context.getResources().getString(R.string.fireStoreDeviceCollection);
        historyCollection = context.getResources().getString(R.string.fireStoreHistoryCollection);
        sharedDeviceId = context.getResources().getString(R.string.sharedPrefDeviceId);
        sharedPref = context.getSharedPreferences("com.example.app", Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public myViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view, get the list item layout
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.device_item, parent, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(lp);
        return new myViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final myViewHolder holder, final int position) {

        final Device device = mDataset.get(position);

        //get the device ID from the hashmap using the MAC
        final String deviceId = mac_id_hashmap.get(device.getMacAddress());
        //get activity from context
        Activity activity = (Activity) context;
        //set the output for the item row
        //set a listener to the document in the device root collection that have the same MAC
        //get the document that has the same MAC address in the root device collection
        //check if there is any update
        //if yes, alert user
        //use a listener registration for removal
        final ListenerRegistration listenerRegistration = mFirestore.collection(deviceCollection).whereEqualTo("macAddress", device.getMacAddress())
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
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
                                String documentID = documentChange.getDocument().getId();
                                Device notiDevice = documentSnapshot.toObject(Device.class);
                                final Alert alert = notiDevice.getAlert();
                                final CollectionReference historyCollectionReference = mFirestore.collection(userCollection)
                                    .document(user.getUid()).collection(deviceCollection).document(deviceId)
                                    .collection(historyCollection);
                                //check if the alert is already existed in the history
                                historyCollectionReference.whereEqualTo("date", alert.getDate()).get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            //if it is not existed
                                            //display the alert and add it to history
                                            if(task.getResult().isEmpty()){
                                                addNotification(device.getName());
                                                //display the date on the item row
                                                holder.deviceAlertDateTextView.setText(
                                                        dateFormat.format(alert.getDate()));
                                                Glide.with(context.getApplicationContext()).load(alert.getImageURL()).into(holder.deviceImageView);
                                                //add the alert to the history collection of the user device document
                                                historyCollectionReference.add(alert);
                                                holder.showImage = true;
                                                holder.deviceImageView.setVisibility(View.VISIBLE);
                                                holder.arrowButton.setImageResource(R.drawable.up);
                                            }
                                        }
                                    });
                            }
                            break;
                    }
                }
                }
            });

        //add the listener registration to the list
        listenerRegistrationList.add(listenerRegistration);

        holder.deviceNameTextView.setText(device.getName());

        //edit device button
        holder.arrowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(holder.showImage) {
                    holder.showImage = false;
                    holder.deviceImageView.setVisibility(View.GONE);
                    holder.arrowButton.setImageResource(R.drawable.down);
                } else {
                    holder.showImage = true;
                    holder.deviceImageView.setVisibility(View.VISIBLE);
                    holder.arrowButton.setImageResource(R.drawable.up);
                }
            }
        });


        holder.editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //create an alert dialog builder
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                //set the title for the dialog
                builder.setTitle("Edit Device Name");

                final LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Set up the input
                final EditText deviceNameAlertEditText = new EditText(context);
                //set the hint
                deviceNameAlertEditText.setHint("Device Name");
                deviceNameAlertEditText.setText(device.getName());
                //add the input to the layout
                layout.addView(deviceNameAlertEditText);
                builder.setView(layout);

                builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        final String deviceName = deviceNameAlertEditText.getText().toString();
                        if(TextUtils.isEmpty(deviceName)) {
                            Toast.makeText(context, "Please enter device name", Toast.LENGTH_LONG).show();
                        } else {
                            device.setName(deviceName);
                            if(user != null) {
                                mFirestore.collection(userCollection).document(user.getUid())
                                        .collection(deviceCollection).document(deviceId).set(device);
                                holder.deviceNameTextView.setText(deviceName);
                            }
                        }
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.cancel();
                    }
                });
                builder.show();

            }
        });

        //remove device button
        holder.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //create an alert dialog builder
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                //set the title for the dialog
                builder.setTitle("Warning");
                builder.setMessage("Do you want to remove this device ?");

                builder.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //get the device ID from the hashmap
                        device.setOwned(false);
                        if(user != null) {
                            mFirestore.collection(userCollection).document(user.getUid())
                                    .collection(deviceCollection).document(deviceId).set(device);
                            mDataset.remove(position);
                            notifyItemRemoved(position);
                            notifyItemChanged(position, mDataset.size());
                            if(listenerRegistrationList.get(position) != null) {
                                listenerRegistrationList.get(position).remove();
                                listenerRegistrationList.remove(position);
                            }
                        }
                    }
                });
               builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int i) {
                       dialog.cancel();
                   }
               });
               builder.show();
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

    public void clear() {
        //remove all the listener
        if(!listenerRegistrationList.isEmpty()) {
            for(int i = 0; i < listenerRegistrationList.size(); i++) {
                listenerRegistrationList.get(i).remove();
            }
            //clear the listener list
            listenerRegistrationList.clear();
        }
        //clear the data list
        final int size = mDataset.size();
        mDataset.clear();
        mac_id_hashmap.clear();
        //notify removal from the adapter
        notifyItemRangeRemoved(0, size);
    }

    private void addNotification(String deviceName) {
        //setup notification
        NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.alert)
                .setContentTitle("New Alert")
                .setContentText("' " + deviceName + " ' has detected a motion")
                .setAutoCancel(true);


        // Add as notification
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
    }
}
