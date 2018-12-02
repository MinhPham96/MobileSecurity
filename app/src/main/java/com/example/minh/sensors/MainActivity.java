package com.example.minh.sensors;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.math.BigInteger;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main Activity";
    private String deviceCollection;
    private String userCollection;
    private String usecaseCollection;
    private static String salt;
    private static final String macAddress = getMacAddr();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private PowerManager.WakeLock wl;

    private ImageButton sensorButton, addDeviceButton;
    private Spinner typeSpinner;
    private TextView usernameTextView;
    private RecyclerView deviceRecyclerView;
    private DeviceAdapter mDeviceAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<Device> deviceList = new ArrayList<>();
    private HashMap<String, String> mac_id_hashmap = new HashMap<>();

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ListenerRegistration listenerRegistration;
    private FirebaseUser user;
    private String userId;
    private boolean initCheck = false, loginState = false;

    private SharedPreferences sharedPref;
    private String sharedDeviceType;
    private int deviceType = 0;
    private String deviceUseCase;
    private List<String> spinnerArray = new ArrayList<>();

    private static final String CHANNEL_ID  = "MS1211";

    private boolean changeActivity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //notification
        createNotificationChannel();

        //setup wakelock
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();

        sharedPref = this.getSharedPreferences("com.example.app", Context.MODE_PRIVATE);
        sharedDeviceType = getResources().getString(R.string.sharedPrefDeviceType);

        userCollection = getResources().getString(R.string.fireStoreUserCollection);
        deviceCollection = getResources().getString(R.string.fireStoreDeviceCollection);
        usecaseCollection = getResources().getString(R.string.fireStoreUseCaseCollection);
        salt = getResources().getString(R.string.salt);

        //setup Firestore and authentication
        mFirestore = FirebaseFirestore.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        addDeviceButton = (ImageButton) findViewById(R.id.addDeviceButton);
        usernameTextView = (TextView) findViewById(R.id.usernameTextView);

        //add the authentication listener
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                user = firebaseAuth.getCurrentUser();
                if(user == null) {
                    //remove listener and clear the recycler view
                    if(listenerRegistration != null){
                        listenerRegistration.remove();
                    }
                    mDeviceAdapter.clear();
                    deviceList.clear();
                    mac_id_hashmap.clear();
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                    initCheck = true;
                } else {
                    storeNewDevice();
                    userId = user.getUid();
                    usernameTextView.setText(user.getDisplayName() + "'s Device List");
                    setupUserDeviceListener();
                    initCheck = true;
                }
            }
        };
        mFirebaseAuth.addAuthStateListener(mAuthListener);

        //setup recycler view
        deviceRecyclerView = (RecyclerView) findViewById(R.id.deviceRecyclerView);
        deviceRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        deviceRecyclerView.setLayoutManager(mLayoutManager);

        mDeviceAdapter = new DeviceAdapter(deviceList, mac_id_hashmap, this);
        deviceRecyclerView.setAdapter(mDeviceAdapter);
        deviceRecyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, MotionEvent e) {
                changeActivity = true;
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

            }
        });


        //register the device to the currently logged in user
        addDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setupAddDeivceAlertDialog();
            }
        });

        //spinner
        typeSpinner = new Spinner(this);
        // Create an ArrayAdapter using the string array and a default spinner layout
//        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
//                R.array.device_type, android.R.layout.simple_spinner_item);
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>
                (this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);

        mFirestore.collection(usecaseCollection).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    for(DocumentSnapshot documentSnapshot: task.getResult().getDocuments()){
                        spinnerArray.add(documentSnapshot.getId());
                    }
                    adapter.notifyDataSetChanged();
                }
            }
        });

        // Specify the layout to use when the list of choices appears
//        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        typeSpinner.setAdapter(adapter);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                //get the device type from the position
                deviceType = position;
                deviceUseCase = spinnerArray.get(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        sensorButton = (ImageButton) findViewById(R.id.sensorButton);
        sensorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setupSensorAlertDialog();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(initCheck && user == null){
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }
        if(changeActivity){
            setupUserDeviceListener();
            changeActivity = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(changeActivity) {
            mDeviceAdapter.clear();
            deviceList.clear();
            mDeviceAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //release wakelock to save power
        wl.release();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.menu, menu);
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_logout) {
            mFirebaseAuth.signOut();
            deviceList.clear();
            mDeviceAdapter.notifyDataSetChanged();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    public void setupUserDeviceListener() {
        listenerRegistration = mFirestore.collection(userCollection).document(userId).collection(deviceCollection)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                if(e != null) {
                    return;
                }
                if(queryDocumentSnapshots != null) {
                    deviceList.clear();
                    mDeviceAdapter.clear();
                    for(QueryDocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Device newDevice = snapshot.toObject(Device.class);
                        //check if the current user own the device or not
                        if(newDevice.isOwned()) {
                            deviceList.add(newDevice);
                            mac_id_hashmap.put(newDevice.getMacAddress(), snapshot.getId());
                        }
                    }
//                    deviceList.addAll(queryDocumentSnapshots.toObjects(Device.class));
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

    public void addDeviceToUser(final String deviceName, String userEmail, String userPassword) {
        mFirestore.collection(userCollection)
                .whereEqualTo("email", userEmail)
                .whereEqualTo("password", SHA512(userPassword, "salt"))
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    String documentID = null;
                    for(DocumentSnapshot documentSnapshot: task.getResult().getDocuments()) {
                        documentID = documentSnapshot.getId();
                    }
                    final String checkUserID = documentID;
                    mFirestore.collection(userCollection).document(checkUserID).collection(deviceCollection)
                        .whereEqualTo("macAddress", macAddress).get()
                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                //if not add new device
                                if(task.getResult().isEmpty()) {
                                    Device device = new Device(deviceName, macAddress, true);
                                    mFirestore.collection(userCollection).document(checkUserID).collection(deviceCollection).add(device);
                                    Toast.makeText(MainActivity.this, "add Device", Toast.LENGTH_SHORT).show();
                                } else {
                                    //check if the isOwned property is true or not
                                    //if true, notify user they already has this device
                                    //else, change to true
                                    for (DocumentSnapshot snapshot : task.getResult()) {
                                        Device device = snapshot.toObject(Device.class);
                                        if(device.isOwned()) {
                                            Toast.makeText(MainActivity.this, "You already had this device", Toast.LENGTH_SHORT).show();
                                        } else {
                                            device.setOwned(true);
                                            device.setName(deviceName);
                                            mFirestore.collection(userCollection).document(checkUserID)
                                                    .collection(deviceCollection).document(snapshot.getId()).set(device);
                                            Toast.makeText(MainActivity.this, "add Device", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            }
                        });
                }

            }
        });
    }

    public void setupAddDeivceAlertDialog() {
        //create an alert dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //set the title for the dialog
        builder.setTitle("Add Device to User");

        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Set up the input
        final EditText deviceNameAlertEditText = new EditText(this);
        //set the hint
        deviceNameAlertEditText.setHint("Device Name");
        //add the input to the layout
        layout.addView(deviceNameAlertEditText);

        //repeat for user email & password
        final EditText emailAlertEditText = new EditText(this);
        emailAlertEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailAlertEditText.setHint("User Email");
        layout.addView(emailAlertEditText);

        final EditText passwordAlertEditText = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        passwordAlertEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordAlertEditText.setHint("User Password");
        layout.addView(passwordAlertEditText);

        builder.setView(layout);

        // Set up the buttons
        builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String deviceName = deviceNameAlertEditText.getText().toString();
                final String userEmail = emailAlertEditText.getText().toString();
                final String userPassword = passwordAlertEditText.getText().toString();

                if(TextUtils.isEmpty(deviceName)) {
                    Toast.makeText(MainActivity.this,"Please enter device name",Toast.LENGTH_LONG).show();
                } else if (TextUtils.isEmpty(userEmail)) {
                    Toast.makeText(MainActivity.this,"Please enter user email",Toast.LENGTH_LONG).show();
                } else if (TextUtils.isEmpty(userPassword)) {
                    Toast.makeText(MainActivity.this,"Please enter user password",Toast.LENGTH_LONG).show();
                } else {
                    addDeviceToUser(deviceName, userEmail, userPassword);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void setupSensorAlertDialog(){
        //create an alert dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //set the title for the dialog
        builder.setTitle("Select Use Case");

        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        //add the input to the layout
        layout.addView(typeSpinner);
//        layout.setLayoutParams(new LinearLayout.LayoutParams(300, 300));

        builder.setView(layout);


        // Set up the buttons
        builder.setPositiveButton("Select", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                moveToSensor();
                changeActivity = true;
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

//        builder.show();
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        alertDialog.getWindow().setLayout(700,500);


    }

    public void moveToSensor() {
        //save the device type to shared preference
//        sharedPref.edit().putInt(sharedDeviceType,deviceType).apply();
        sharedPref.edit().putString(sharedDeviceType, deviceUseCase).apply();
        Intent intent = new Intent(MainActivity.this, SensorActivity.class);
        startActivity(intent);
    }

    //hashing
    public static String md5(String s)
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes(Charset.forName("US-ASCII")),0,s.length());
            byte[] magnitude = digest.digest();
            BigInteger bi = new BigInteger(1, magnitude);
            String hash = String.format("%0" + (magnitude.length << 1) + "x", bi);
            return hash;
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        return "";
    }

    public static String SHA512(String passwordToHash, String salt){
        String generatedPassword = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest(passwordToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for(int i=0; i< bytes.length ;i++){
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            generatedPassword = sb.toString();
        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        return generatedPassword;
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
//                return BCrypt.hashpw(res1.toString(), BCrypt.gensalt());
                return SHA512(res1.toString(), "salt");
//                return res1.toString();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "02:00:00:00:00:00";
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    private void takeSnapshot(){

    }

    private void openCamera() {
    }


}
