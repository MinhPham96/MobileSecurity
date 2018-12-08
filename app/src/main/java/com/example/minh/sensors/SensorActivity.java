package com.example.minh.sensors;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class SensorActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "Sensor Activity";
    private String deviceCollection, useCaseCollection;
    private static final String macAddress = MainActivity.getMacAddr();

    private PowerManager.WakeLock wl;

    private FirebaseFirestore mFirestore;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseUser user;
    private DocumentReference deviceDocRef;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mPhotoStorageReferece;

    private SensorManager sensorManager;
    private Sensor sensor;
    private TextView counterTextView, peakTextView;
    private ImageButton runButton, feedbackButton;
    private float ax, filteredData, thresholdAlpha, thresholdBeta;
    private Queue<Float> dataThresholdQueue = new LinkedList<>();
    private Queue<Float> dataTransferQueue = new LinkedList<>();
    private float dataThresholdSum = 0.0f;
    private float dataThresholdMean = 0.0f;
    private float dataTransferSum = 0.0f;
    private float dataTransferMean = 0.0f;
    private static final int dataThresholdSize = 100;
    private static final int dataTransferSize = 10;
    private static final float alphaValue = 1.5f;
    private static final float betaValue = 0.2f;
    private static final float minValue = 0.01f;
    private double adaptiveThreshold = 0.0f;
    private boolean sensorIsRun = false;
    private String[] feedbacks = new String[]{"The alert is correct", "The alert is false", "No alert receive"};

    //low pass filter
//    private float timeConstant = 0.18f;
    //time constant formula: time constant = wanted alpha * period  / (1 - period)
    private float timeConstant = 0.075f;
    private float filterAlpha[] = new float[]{0.1f, 0.1f, 0.1f};
    private float dt = 0;
    // Timestamps for the low-pass filters
    private float timestamp = System.nanoTime();
    private float timestampOld = System.nanoTime();
    // Gravity and linear accelerations components for the Wikipedia low-pass filter
    private float[] gravity = new float[]{ 0, 0, 0 };
    private float[] linearAcceleration = new float[]{ 0, 0, 0 };
    // Raw accelerometer data
    private float[] input = new float[]{ 0, 0, 0 };
    private int count = 0;

    private boolean eventTrigger = false;
    private int eventCounter = 0;
    private float peak = 0f;
    private float submitPeak = 0f;
    private static final float minimumPeak  = 0.05f;
    private boolean hasEvent = false;

    private final Handler mHandler = new Handler();
    private LineGraphSeries<DataPoint> mDataSeries;
    private LineGraphSeries<DataPoint> mDataTransferSeries;
    private LineGraphSeries<DataPoint> mAlphaThresholdSeries;
    private LineGraphSeries<DataPoint> mBetaThresholdSeries;

    private float dataLastXValue = 5f;

    private SharedPreferences sharedPref;
    private String sharedDeviceType;
    private int deviceType = 0;
//    private String[] deviceTypeName;
    private String deviceUseCase;
    private int selectedFeedback = 0;
    private boolean feedbackAvailable = false;

    //Camera
    private TextureView textureView;
    private boolean cameraOn = true;

    //Check state orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,270);
        ORIENTATIONS.append(Surface.ROTATION_90,180);
        ORIENTATIONS.append(Surface.ROTATION_180,0);
        ORIENTATIONS.append(Surface.ROTATION_270,90);
    }

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;

    //Save to FILE
    private File file;
    private Uri photoURI;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int imageQuality = 100;
    private static final int imageWidth = 320;
    private static final int imageHeight = 240;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice=null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        deviceCollection = getResources().getString(R.string.fireStoreDeviceCollection);
        useCaseCollection = getResources().getString(R.string.fireStoreUseCaseCollection);

        //setup wakelock
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();

        //get the device type from the shared preference
        sharedPref = this.getSharedPreferences("com.example.app", Context.MODE_PRIVATE);
        sharedDeviceType = getResources().getString(R.string.sharedPrefDeviceType);
//        deviceType = sharedPref.getInt(sharedDeviceType, 0);
        deviceUseCase = sharedPref.getString(sharedDeviceType, "swing_door");
//        deviceTypeName = getResources().getStringArray(R.array.device_type);
        Toast.makeText(SensorActivity.this, deviceUseCase, Toast.LENGTH_SHORT).show();

        mFirebaseAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Intent intent = new Intent(SensorActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    runButton.setVisibility(View.VISIBLE);
                }
            }
        };
        mFirebaseAuth.addAuthStateListener(mAuthListener);

        mFirestore = FirebaseFirestore.getInstance();
        //temporary path for the device doc ref
        deviceDocRef = mFirestore.collection(deviceCollection).document("current_user");
        //check if the Firestore has the current device, if yes, set the path for the document
        mFirestore.collection(deviceCollection).whereEqualTo("macAddress", macAddress)
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()) {
                    for(DocumentSnapshot documentSnapshot : task.getResult().getDocuments()) {
                        deviceDocRef = mFirestore.collection(deviceCollection).document(documentSnapshot.getId());
                    }
                }
            }
        });

        //get the adaptive threshold from the database
        mFirestore.collection(useCaseCollection).document(deviceUseCase).get()
            .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if(task != null) {
                        //alpha is not needed when using the adaptive threshold
                        adaptiveThreshold = (double) task.getResult().get("threshold");
                        //only beta is required
                        thresholdBeta = ((float)adaptiveThreshold * betaValue) + minValue;
//                        Toast.makeText(SensorActivity.this, String.valueOf(adaptiveThreshold), Toast.LENGTH_SHORT).show();
                    }
                }
            });

        //cloud storage
        mFirebaseStorage = FirebaseStorage.getInstance();
        mPhotoStorageReferece = mFirebaseStorage.getReference().child("alert");

        counterTextView = (TextView) findViewById(R.id.counterTextView);
        peakTextView = (TextView) findViewById(R.id.peakTextView);

        runButton = (ImageButton) findViewById(R.id.runButton);
        runButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensorIsRun = !sensorIsRun;
                if(sensorIsRun) runButton.setImageResource(R.drawable.pause);
                else runButton.setImageResource(R.drawable.play);
            }
        });

        feedbackButton = (ImageButton) findViewById(R.id.feedbackButton);
        feedbackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!sensorIsRun) {
                    //create an alert dialog builder
                    final AlertDialog.Builder builder = new AlertDialog.Builder(SensorActivity.this);
                    //set the title for the dialog
                    builder.setTitle("Select Feedback");
                    builder.setSingleChoiceItems(feedbacks, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            selectedFeedback = i;
                        }
                    });
                    builder.setPositiveButton("Submit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            Data newData = new Data(submitPeak, new Date());
                            //depends on the feedback, the data will be stored in correct or false data collection
                            if(selectedFeedback == 1) {
                                mFirestore.collection(useCaseCollection).document(deviceUseCase)
                                        .collection("false_data").add(newData);
                            } else {
                                mFirestore.collection(useCaseCollection).document(deviceUseCase)
                                        .collection("correct_data").add(newData);
                            }
                            dialog.dismiss();
                            //the feedback only for latest reading
                            //so feedback is unavailable after submit
                            feedbackButton.setVisibility(View.INVISIBLE);
                            feedbackAvailable = false;
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                } else {
                    Toast.makeText(SensorActivity.this,
                            "Please pause the sensor before sending feedback",
                            Toast.LENGTH_SHORT).show();
                }

            }
        });

        sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //value in microsecond
        //SENSOR_DELAY_FASTEST: 0
        //SENSOR_DELAY_GAME: 20000
        //SENSOR_DELAY_UI: 66667
        //SENSOR_DELAY_NORMAL: 200000
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        GraphView graph = (GraphView) this.findViewById(R.id.graph);
        mDataSeries = new LineGraphSeries<>();

        mDataTransferSeries = new LineGraphSeries<>();
        mDataTransferSeries.setColor(Color.GREEN);

        mAlphaThresholdSeries = new LineGraphSeries<>();
        mAlphaThresholdSeries.setColor(Color.RED);

        mBetaThresholdSeries = new LineGraphSeries<>();
        mBetaThresholdSeries.setColor(Color.YELLOW);

        graph.addSeries(mDataSeries);
        graph.addSeries(mDataTransferSeries);
        graph.addSeries(mAlphaThresholdSeries);
        graph.addSeries(mBetaThresholdSeries);

        //set the bound for the x axis to manual
        //set the min and max value for x axis
        //this means there will only have 40 values appear on the graph view
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(40);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setScalableY(false);
        graph.getViewport().setMaxY(1.5);
        graph.getViewport().setMinY(-1.5);

        //camera
        textureView = (TextureView)findViewById(R.id.textureView);
        //From Java 1.4 , you can use keyword 'assert' to check expression true or false
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if(sensorIsRun) {
            dataLastXValue += 1d;
            //since the data is filtered and sorted, the max is the last value
            filteredData = addSamples(sensorEvent.values)[2];
            mDataSeries.appendData(new DataPoint(dataLastXValue, ax), true, 40);

            //getting the first 10 samples
            if(dataTransferQueue.size() < dataTransferSize) {
                dataTransferQueue.offer(filteredData);
                dataTransferSum += filteredData;
                //calculate the alpha for each axis
                calculateAlpha(sensorEvent.values);
            }

            //from the 11th sample and forward, keep the latest 10
            if(dataTransferQueue.size() >= dataTransferSize) {
                dataTransferSum -= dataTransferQueue.poll();
                dataTransferQueue.offer(filteredData);
                dataTransferSum += filteredData;
                dataTransferMean = dataTransferSum / dataTransferSize;
                mDataTransferSeries.appendData(new DataPoint(dataLastXValue, dataTransferMean), true, 40);
//                System.out.println("Data Transfer: " + String.valueOf(dataLastXValue) + new ArrayList(dataTransferQueue));

                //adaptive threshold***********************************************************************
                if(dataTransferMean > minimumPeak && dataTransferMean > peak) {
                    peak = dataTransferMean;
                    peakTextView.setText("Peak: " + String.valueOf(peak));
                }

                mAlphaThresholdSeries.appendData(new DataPoint(dataLastXValue, adaptiveThreshold), true, 40);
                mBetaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdBeta), true, 40);

                //if the data transfer pass beta, mark as start time
                if(dataTransferMean > thresholdBeta && !hasEvent) {
                    //reset the peak when there is a reading
                    peak = 0;
                    feedbackAvailable = true;
                    hasEvent = true;

                } else if (dataTransferMean < thresholdBeta && hasEvent) {
                    //the submit peak will be recorded when the event is done
                    if(feedbackAvailable)feedbackButton.setVisibility(View.VISIBLE);
                    submitPeak = peak;
                    eventTrigger = false;
                    hasEvent = false;
                }

                //if the data transfer pass the alpha, mark an event
                if(!eventTrigger && (dataTransferMean > adaptiveThreshold)) {
                    eventTrigger = true;
                    takePicture();
                    eventCounter += 1;
                    counterTextView.setText("Event Counter: " + String.valueOf(eventCounter));
                }
            }
        }
    }



    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
//        if (user == null) {
//            Intent intent = new Intent(SensorActivity.this, MainActivity.class);
//            startActivity(intent);
//            finish();
//        }
        startBackgroundThread();
        if(textureView.isAvailable()) {
            openCamera();
        }
        else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        stopBackgroundThread();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //release wakelock and unregister sensor to save power
        wl.release();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public void calculateAlpha(float[] axisData) {
        //scan through the gravity reach of each axis
        for(int i = 0; i < axisData.length; i++) {
            //determine the alpha for each axis
            if(Math.abs(axisData[i]) <= 3) {
                filterAlpha[i] = 0.5f;
            } else if (Math.abs(axisData[i]) <= 7) {
                filterAlpha[i] = 0.3f;
            } else if (Math.abs(axisData[i]) <= 10) {
                filterAlpha[i] = 0.1f;
            }
        }
    }

    public float[] addSamples(float[] acceleration)
    {
        // Get a local copy of the sensor values
        System.arraycopy(acceleration, 0, this.input, 0, acceleration.length);

        gravity[0] = filterAlpha[0] * gravity[0] + (1 - filterAlpha[0]) * input[0];
        gravity[1] = filterAlpha[1] * gravity[1] + (1 - filterAlpha[1]) * input[1];
        gravity[2] = filterAlpha[2] * gravity[2] + (1 - filterAlpha[2]) * input[2];

        //get the linear acceleration convert them to absolute values
        linearAcceleration[0] = Math.abs(input[0] - gravity[0]);
        linearAcceleration[1] = Math.abs(input[1] - gravity[1]);
        linearAcceleration[2] = Math.abs(input[2] - gravity[2]);
        //sort the arrays to get the max value
        Arrays.sort(linearAcceleration);

        return linearAcceleration;
    }

    //camera
    private void takePicture() {
        if(cameraDevice == null)
            return;
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null)
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);

            //Capture image with custom size
            final ImageReader reader = ImageReader.newInstance(imageWidth,imageHeight,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
//            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) imageQuality);

            //Check orientation base on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));

//            file = new File(Environment.getExternalStorageDirectory()+"/"+ UUID.randomUUID().toString()+".jpg");
            file = createImageFile();
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try{
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);

                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    finally {
                        {
                            if(image != null)
                                image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    Log.d(TAG, "Start Saving Image");
                    final Bitmap photo= BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    new Handler(Looper.getMainLooper()).post(new Runnable(){
                        @Override
                        public void run() {
                            if (file != null) {
                                FileOutputStream fout;
                                try {
                                    // compress and then flush output file
                                    fout = new FileOutputStream(file);
                                    photo.compress(Bitmap.CompressFormat.PNG, 50, fout);
                                    fout.flush();
                                    Log.d(TAG, "Saved Image");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                // obtaining Uri from flushed file
                                photoURI = Uri.fromFile(file);
                                StorageReference photoReference = mPhotoStorageReferece.child(photoURI.getLastPathSegment());
                                photoReference.putFile(photoURI).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        //get the URI from the task snapshot
                                        Task<Uri> result = taskSnapshot.getMetadata().getReference().getDownloadUrl();
                                        result.addOnSuccessListener(new OnSuccessListener<Uri>() {
                                            @Override
                                            public void onSuccess(Uri uri) {
                                                //convert the URI to string
                                                String photoStringLink = uri.toString();
                                                Log.d(TAG, "Uploaded Image");
                                                Alert alert = new Alert(photoStringLink, new Date());
                                                //update this document to alert the user
                                                Device device = new Device(macAddress, alert);
                                                deviceDocRef.set(device).addOnSuccessListener(new OnSuccessListener<Void>() {
                                                    @Override
                                                    public void onSuccess(Void aVoid) {
                                                        Log.i(TAG, "Send Alert");
                                                    }
                                                });
                                                Toast.makeText(SensorActivity.this, "Alert sent", Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                    }
                                });
                            }
                        }
                    });
                }
            };

            reader.setOnImageAvailableListener(readerListener,mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
//                    Toast.makeText(SensorActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        cameraCaptureSession.capture(captureBuilder.build(),captureListener,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreview() {
        try{
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) imageQuality);


            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(SensorActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
//        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) imageQuality);

        try{
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            //0: back camera
            //1: front camera
            cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            System.out.println("Device Level: " + String.valueOf(deviceLevel));
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        android.Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId,stateCallback,null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread= null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    // function to create temporary file
    public File createImageFile() {
        Log.i(TAG, "create temporary image file");
        // create timestamp for file
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        // create file name
        String imageFileName = "JPEG_" + timeStamp + "_";
        // create empty file
        File mFileTemp = null;
        // create string story file directory
        String root= SensorActivity.this.getDir("my_sub_dir", Context.MODE_PRIVATE).getAbsolutePath();
        // create file representing directory
        File myDir = new File(root + "/Img");
        // create directory if it does not exist
        if(!myDir.exists()){
            myDir.mkdirs();
        }
        try {
            //  store file at directory into temporary file
            mFileTemp=File.createTempFile(imageFileName,".jpeg",myDir.getAbsoluteFile());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        // return the temporary file
        return mFileTemp;
    }
}
