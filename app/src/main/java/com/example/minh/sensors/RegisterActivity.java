package com.example.minh.sensors;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "Register Activity";
    private String userCollection;

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    private EditText registerNameEditText, registerEmailEditText,
            registerPasswordEditText;
    private Button registerAccountButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userCollection = getResources().getString(R.string.fireStoreUserCollection);

        mFirestore = FirebaseFirestore.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        registerNameEditText = (EditText) findViewById(R.id.registerNameEditText);
        registerEmailEditText = (EditText) findViewById(R.id.registerEmailEditText);
        registerPasswordEditText = (EditText) findViewById(R.id.registerPasswordEditText);
        registerAccountButton = (Button) findViewById(R.id.registerAccountButton);

        registerAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                registerAccount();
            }
        });

    }

    public void registerAccount() {
        final String email = registerEmailEditText.getText().toString().trim();
        final String password = registerPasswordEditText.getText().toString().trim();
        final String name = registerNameEditText.getText().toString();

        if(!isDataConnectionAvailable(RegisterActivity.this)) {
            Toast.makeText(this,"Please connect to Internet",Toast.LENGTH_LONG).show();
            return;
        }

        if(TextUtils.isEmpty(email)) {
            Toast.makeText(this,"Please enter email",Toast.LENGTH_LONG).show();
            return;
        }

        if(TextUtils.isEmpty(password)) {
            Toast.makeText(this,"Please enter password",Toast.LENGTH_LONG).show();
            return;
        }

        if(password.length() < 6) {
            Toast.makeText(this,"Password too short, please enter minimum of 6 characters",Toast.LENGTH_LONG).show();
            return;
        }

        if(TextUtils.isEmpty(name)) {
            Toast.makeText(this,"Please enter name",Toast.LENGTH_LONG).show();
            return;
        }

        //create user using email and password
        mFirebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                //when complete the app will automatically login
                //get the current user
                FirebaseUser firebaseUser = mFirebaseAuth.getCurrentUser();
                //update the user display name
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(name).build();
                firebaseUser.updateProfile(profileUpdates);
                //create new user and store in firestore
                User user = new User(name, email, password);
                DocumentReference userDocRef = mFirestore.collection(userCollection)
                        .document(firebaseUser.getUid());
                userDocRef.set(user).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        //when done move back to main activity
                        Log.i(TAG, "Store User");
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
            }
        });
    }

    public static boolean isDataConnectionAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }
}
