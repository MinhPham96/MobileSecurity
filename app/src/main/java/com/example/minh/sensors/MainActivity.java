package com.example.minh.sensors;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main Activity";
    private static final String alertCollection = "alerts";
    private static final String historyCollection = "history" ;
    private static final String deviceCollection = "devices" ;
    private static final String macAddress = getMacAddr();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private Button onlineButton, loginButton;
    private TextView newAlertTextView, macTextView, usernameTextView;
    private RecyclerView alertRecyclerView;
    private AlertAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<Alert> alertList = new ArrayList<>();

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
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
        usernameTextView = (TextView) findViewById(R.id.usernameTextView);
        newAlertTextView = (TextView) findViewById(R.id.newAlertTextView);

        //add the authentication listener
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user == null) {
                    loginButton.setText("Login");
                    loginState = false;
                    usernameTextView.setText("Username");
                } else {
                    loginButton.setText("logout");
                    loginState = true;
                    usernameTextView.setText(user.getDisplayName());
                }
            }
        };
        mFirebaseAuth.addAuthStateListener(mAuthListener);

        //*NOTE*: Currently, the app is running on online mode
        //check if the Firestore has the current device
        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", macAddress)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()) {
                    for(DocumentSnapshot documentSnapshot : task.getResult().getDocuments()) {
                        //set the path for the device document
                        macTextView.setText(documentSnapshot.getId());
                        alertDocRef = mFirestore.collection(deviceCollection).document(documentSnapshot.getId());
                        //since the listener only works with ths setup
                        //put the function outside here will not work
                        setAlertListener();
                    }
                }
            }
        });

        //configure the login button depends on the login state
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(loginState) {
                    mFirebaseAuth.signOut();
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
                addNewDevice();
            }
        });

        //setup recycler view
        alertRecyclerView = (RecyclerView) findViewById(R.id.alertRecyclerView);
        alertRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        alertRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new AlertAdapter(alertList);
        alertRecyclerView.setAdapter(mAdapter);

    }

    @Override
    protected void onStart() {
        super.onStart();
        //get all the document in collection "history"
        //order them by the date
        mFirestore.collection(historyCollection).orderBy("date", Query.Direction.ASCENDING)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()) {
                    //clear the alertList before adding new item
                    alertList.clear();
                    //convert the result into a list of alert
                    alertList.addAll(task.getResult().toObjects(Alert.class));
                    mAdapter.notifyDataSetChanged();

                    Log.i(TAG,"got data");
                }
            }
        });
    }

    public void addNewDevice() {
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
