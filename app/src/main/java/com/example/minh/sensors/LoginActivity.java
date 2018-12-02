package com.example.minh.sensors;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "Login Activity";

    private FirebaseAuth mFirebaseAuth;

    private EditText loginEmailEditText;
    private EditText loginPasswordEditText;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mFirebaseAuth = FirebaseAuth.getInstance();

        loginEmailEditText = (EditText) findViewById(R.id.loginEmailEditText);
        loginPasswordEditText = (EditText) findViewById(R.id.loginPasswordEditText);
        loginButton = (Button) findViewById(R.id.loginButton);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginAccount();
            }
        });

    }

    public void loginAccount() {
        final String email = loginEmailEditText.getText().toString().trim();
        final String password = loginPasswordEditText.getText().toString().trim();

        if(TextUtils.isEmpty(email)) {
            Toast.makeText(this,"Please enter email",Toast.LENGTH_LONG).show();
            return;
        }

        if(TextUtils.isEmpty(password)) {
            Toast.makeText(this,"Please enter password",Toast.LENGTH_LONG).show();
            return;
        }

        //sign in using email and password
        mFirebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (!task.isSuccessful()) {
                        //Log.w("TAG", "signInWithEmail:failed", task.getException());
                        Toast.makeText(LoginActivity.this, "Failed log in", Toast.LENGTH_SHORT).show();
                    } else {
                        //if log in, move back to main activity
                        checkIfEmailVerified();
                    }
                }
            });
    }

    public void moveToRegisterActivity(View view) {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void checkIfEmailVerified()
    {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user.isEmailVerified())
        {
            // user is verified, so you can finish this activity or send user to activity which you want.
            finish();
            Toast.makeText(LoginActivity.this, "Successfully logged in", Toast.LENGTH_SHORT).show();
//            finish();
        }
        else
        {
            // email is not verified, so just prompt the message to the user and restart this activity.
            // NOTE: don't forget to log out the user.
            mFirebaseAuth.signOut();
            Toast.makeText(LoginActivity.this, "Email is not verified", Toast.LENGTH_SHORT).show();
            //restart this activity
        }
    }

}
