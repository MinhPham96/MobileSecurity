package com.example.minh.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "History Activity";
    private String deviceCollection;
    private String userCollection;
    private String historyCollection;
    private String sharedDeviceId;

    private RecyclerView alertRecyclerView;
    private AlertAdapter mAlertAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<Alert> alertList = new ArrayList<>();

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseUser user;
    private String userId;

    private SharedPreferences sharedPref;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        userCollection = getResources().getString(R.string.fireStoreUserCollection);
        deviceCollection = getResources().getString(R.string.fireStoreDeviceCollection);
        historyCollection = getResources().getString(R.string.fireStoreHistoryCollection);
        sharedDeviceId = getResources().getString(R.string.sharedPrefDeviceId);

        sharedPref = this.getSharedPreferences("com.example.app", Context.MODE_PRIVATE);
        deviceId = sharedPref.getString(sharedDeviceId, null);

        //setup Firestore and authentication
        mFirestore = FirebaseFirestore.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        //add the authentication listener
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
            user = firebaseAuth.getCurrentUser();
            if(user != null) {
                userId = user.getUid();
                getAlertHistoryList();
            }
            }
        };
        mFirebaseAuth.addAuthStateListener(mAuthListener);

        //setup recycler view
        alertRecyclerView = (RecyclerView) findViewById(R.id.alertRecyclerView);
        alertRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        alertRecyclerView.setLayoutManager(mLayoutManager);

        mAlertAdapter = new AlertAdapter(alertList);
        alertRecyclerView.setAdapter(mAlertAdapter);

    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    public void getAlertHistoryList(){
        //add activity to snapshot listener to automatically remove the listener when the activity stop
//        mFirestore.collection(userCollection).document(userId)
//            .collection(deviceCollection).document(deviceId)
//            .collection(historyCollection)
//            .addSnapshotListener(HistoryActivity.this , new EventListener<QuerySnapshot>() {
//                @Override
//                public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
//                    if(e != null) {
//                        return;
//                    } if(queryDocumentSnapshots != null) {
//                        alertList.clear();
//                        alertList.addAll(queryDocumentSnapshots.toObjects(Alert.class));
//                        mAlertAdapter.notifyDataSetChanged();
//                    }
//                }
//            });

        //get the whole history collection ordered by the date
        mFirestore.collection(userCollection).document(userId)
                .collection(deviceCollection).document(deviceId)
                .collection(historyCollection)
                .orderBy("date", Query.Direction.DESCENDING)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                alertList.clear();
                alertList.addAll(task.getResult().toObjects(Alert.class));
                mAlertAdapter.notifyDataSetChanged();
            }
        });
    }
}
