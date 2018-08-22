package com.example.minh.sensors;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main Activity";
    private static final String alertCollection = "alerts";
    private static final String historyCollection = "history" ;
    private static final String deviceCollection = "devices" ;
    private static final String userCollection = "users";
    private static final String macAddress = getMacAddr();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private Button onlineButton, loginButton, addDeviceButton;
    private TextView newAlertTextView, macTextView, usernameTextView;
    private EditText deviceNameEditText;
    private RecyclerView deviceRecyclerView;
    private DeviceAdapter mDeviceAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<Device> deviceList = new ArrayList<>();

//    private RecyclerView alertRecyclerView;
//    private AlertAdapter mAdapter;
//    private RecyclerView.LayoutManager mLayoutManager;
//    private List<Alert> alertList = new ArrayList<>();

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseUser user;
    private String userId;
    private DocumentReference alertDocRef;
    private boolean initCheck = false, loginState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //setup Firestore and authentication
        mFirestore = FirebaseFirestore.getInstance();
        alertDocRef = mFirestore.collection(alertCollection).document("current_user");
        mFirebaseAuth = FirebaseAuth.getInstance();

        loginButton = (Button) findViewById(R.id.loginButton);
        addDeviceButton = (Button) findViewById(R.id.addDeviceButton);
        deviceNameEditText = (EditText) findViewById(R.id.deviceNameEditText);
        usernameTextView = (TextView) findViewById(R.id.usernameTextView);
        newAlertTextView = (TextView) findViewById(R.id.newAlertTextView);

        //add the authentication listener
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                user = firebaseAuth.getCurrentUser();
                if(user == null) {
                    loginButton.setText("Login");
                    loginState = false;
                    usernameTextView.setText("Username");
                    addDeviceButton.setVisibility(View.GONE);
                    deviceNameEditText.setVisibility(View.GONE);
                    addDeviceButton.setClickable(false);
                } else {
                    userId = user.getUid();
                    loginButton.setText("logout");
                    loginState = true;
                    usernameTextView.setText(user.getDisplayName());
                    addDeviceButton.setVisibility(View.VISIBLE);
                    deviceNameEditText.setVisibility(View.VISIBLE);
                    addDeviceButton.setClickable(true);
                    setupUserDeviceListener();
                }
            }
        };
        mFirebaseAuth.addAuthStateListener(mAuthListener);

        //*NOTE*: Currently, the app is running on online mode
        //check if the Firestore has the current device
//        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", macAddress)
//                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
//            @Override
//            public void onComplete(@NonNull Task<QuerySnapshot> task) {
//                if(task.isSuccessful()) {
//                    for(DocumentSnapshot documentSnapshot : task.getResult().getDocuments()) {
//                        //set the path for the device document
//                        macTextView.setText(documentSnapshot.getId());
//                        alertDocRef = mFirestore.collection(deviceCollection).document(documentSnapshot.getId());
//                        //since the listener only works with ths setup
//                        //put the function outside here will not work
//                        setAlertListener();
//                    }
//                }
//            }
//        });

        //setup recycler view
        deviceRecyclerView = (RecyclerView) findViewById(R.id.deviceRecyclerView);
        deviceRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        deviceRecyclerView.setLayoutManager(mLayoutManager);

        mDeviceAdapter = new DeviceAdapter(deviceList);
        deviceRecyclerView.setAdapter(mDeviceAdapter);

        //configure the login button depends on the login state
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(loginState) {
                    //when sign out, clear the user device list
                    mFirebaseAuth.signOut();
                    deviceList.clear();
                    mDeviceAdapter.notifyDataSetChanged();
                } else {
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                }
            }
        });

        macTextView = (TextView) findViewById(R.id.macTextView);
        macTextView.setText(macAddress);

        onlineButton = (Button) findViewById(R.id.onlineButton);
        onlineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                storeNewDevice();
            }
        });

        //register the device to the currently logged in user
        addDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addNewDeviceToUser();
            }
        });

//        alertRecyclerView = (RecyclerView) findViewById(R.id.alertRecyclerView);
//        alertRecyclerView.setHasFixedSize(true);
//
//        mLayoutManager = new LinearLayoutManager(this);
//        alertRecyclerView.setLayoutManager(mLayoutManager);
//
//        mAdapter = new AlertAdapter(alertList);
//        alertRecyclerView.setAdapter(mAdapter);

    }

    @Override
    protected void onStart() {
        super.onStart();
//        setupHistoryCollectionListener();
        //setup the device list if user is logged in
        if(user != null) {
            setupUserDeviceListener();
        }
    }

//    public void setupAlertHistoryListener() {
//        //get all the document in collection "history"
//        //order them by the date
//        mFirestore.collection(historyCollection).orderBy("date", Query.Direction.ASCENDING)
//                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
//            @Override
//            public void onComplete(@NonNull Task<QuerySnapshot> task) {
//                if(task.isSuccessful()) {
//                    //clear the alertList before adding new item
//                    alertList.clear();
//                    //convert the result into a list of alert
//                    alertList.addAll(task.getResult().toObjects(Alert.class));
//                    mAdapter.notifyDataSetChanged();
//
//                    Log.i(TAG,"got data");
//                }
//            }
//        });
//    }

    public void setupUserDeviceListener() {
        mFirestore.collection(userCollection).document(userId).collection(deviceCollection)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()) {
                    deviceList.clear();
                    deviceList.addAll(task.getResult().toObjects(Device.class));
                    mDeviceAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    public void storeNewDevice() {
        //check if the current device is stored in the Firestore or not
        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", macAddress)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                //if not
                if(task.getResult().isEmpty()) {
                    //add new device to Firestore
                    Device device = new Device(macAddress, null);
                    mFirestore.collection(deviceCollection).add(device).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            Log.i(TAG, "save device");
                        }
                    });
                }
            }
        });
    }

    public void addNewDeviceToUser() {
        final String deviceName = deviceNameEditText.getText().toString();

        if(TextUtils.isEmpty(deviceName)) {
            Toast.makeText(this,"Please enter name",Toast.LENGTH_LONG).show();
            return;
        }

        mFirestore.collection(userCollection).document(userId).collection(deviceCollection)
            .whereEqualTo("macAddress", macAddress).get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if(task.getResult().isEmpty()) {
                        Device device = new Device(deviceName, macAddress, true);
                        mFirestore.collection(userCollection).document(userId).collection(deviceCollection).add(device);
                    }
                }
            });
    }

    public void setAlertListener() {
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

    public void moveToSensor(View view) {
        Intent intent = new Intent(MainActivity.this, SensorActivity.class);
        startActivity(intent);
    }

    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:",b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "02:00:00:00:00:00";
    }
}
