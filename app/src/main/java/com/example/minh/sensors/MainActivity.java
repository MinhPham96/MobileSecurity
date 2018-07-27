package com.example.minh.sensors;

import android.content.Intent;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main Activity";
    private static final String alertCollection = "alerts";
    private static final String historyCollection = "history" ;

    private TextView newAlertTextView;
    private RecyclerView alertRecyclerView;
    private AlertAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<Alert> alertList = new ArrayList<>();

    private FirebaseFirestore mFirebaseFirestore;
    private DocumentReference alertDocRef;
    private boolean initCheck = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseFirestore = FirebaseFirestore.getInstance();
        alertDocRef = mFirebaseFirestore.collection(alertCollection).document("current_user");

        newAlertTextView = (TextView) findViewById(R.id.newAlertTextView);

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
                        newAlertTextView.setText(documentSnapshot.toObject(Alert.class).getDate());
                    }
                    initCheck = true;
                    Log.i(TAG, "Current data: " + documentSnapshot.getData());
                } else {
                    Log.i(TAG, "Current data: null");
                    initCheck = true;
                }
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
        mFirebaseFirestore.collection(historyCollection).orderBy("date", Query.Direction.ASCENDING)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()) {
                    //clear the alertList before adding new item
                    alertList.clear();
//                    for(QueryDocumentSnapshot document : task.getResult()) {
//                        alertList.add(document.toObject(Alert.class));
//                    }
                    //convert the result into a list of alert
                    alertList.addAll(task.getResult().toObjects(Alert.class));
                    mAdapter.notifyDataSetChanged();

                    Log.i(TAG,"got data");
                }
            }
        });
    }

    public void moveToSensor(View view) {
        Intent intent = new Intent(MainActivity.this, SensorActivity.class);
        startActivity(intent);
    }
}
