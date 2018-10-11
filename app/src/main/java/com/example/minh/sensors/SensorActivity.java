package com.example.minh.sensors;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

public class SensorActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "Sensor Activity";
    private String deviceCollection;
    private static final String macAddress = MainActivity.getMacAddr();

    private PowerManager.WakeLock wl;

    private FirebaseFirestore mFirestore;
    private DocumentReference deviceDocRef;


    private float startTime, stopTime;
    private boolean checkStartTime = false;
    private boolean checkStopTime = false;

    private SensorManager sensorManager;
    private Sensor sensor;
    private TextView counterTextView, peakTextView, startTimeTextView, stopTimeTextView;
    private TextView xAxisTextView, yAxisTextView, zAxisTextView;
    private Button runButton, feedbackButton;
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
    private static final float betaValue = 0.5f;
    private static final float minValue = 0.01f;
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
    private static final float minimumPeak  = 0.05f;

    private final Handler mHandler = new Handler();
    private LineGraphSeries<DataPoint> mDataSeries;
    private LineGraphSeries<DataPoint> mDataTransferSeries;
    private LineGraphSeries<DataPoint> mAlphaThresholdSeries;
    private LineGraphSeries<DataPoint> mBetaThresholdSeries;

    private float dataLastXValue = 5f;

    private SharedPreferences sharedPref;
    private String sharedDeviceType;
    private int deviceType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        deviceCollection = getResources().getString(R.string.fireStoreDeviceCollection);

        //setup wakelock
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();

        //get the device type from the shared preference
        sharedPref = this.getSharedPreferences("com.example.app", Context.MODE_PRIVATE);
        sharedDeviceType = getResources().getString(R.string.sharedPrefDeviceType);
        deviceType = sharedPref.getInt(sharedDeviceType, 0);
//        Toast.makeText(SensorActivity.this, String.valueOf(deviceType), Toast.LENGTH_SHORT).show();

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

        counterTextView = (TextView) findViewById(R.id.counterTextView);
        peakTextView = (TextView) findViewById(R.id.peakTextView);
        startTimeTextView = (TextView) findViewById(R.id.startTimeTextView);
        stopTimeTextView = (TextView) findViewById(R.id.stopTimeTextView);

        xAxisTextView = (TextView) findViewById(R.id.xAxisTextView);
        yAxisTextView = (TextView) findViewById(R.id.yAxisTextView);
        zAxisTextView = (TextView) findViewById(R.id.zAxisTextView);

        runButton = (Button) findViewById(R.id.runButton);
        runButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensorIsRun = !sensorIsRun;
                if(sensorIsRun) runButton.setText("Pause");
                else runButton.setText("Run");
            }
        });

        feedbackButton = (Button) findViewById(R.id.feedbackButton);
        feedbackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //create an alert dialog builder
                final AlertDialog.Builder builder = new AlertDialog.Builder(SensorActivity.this);
                //set the title for the dialog
                builder.setTitle("Select Feedback");
                builder.setSingleChoiceItems(feedbacks, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //nothing for now
                    }
                });
                builder.setPositiveButton("Submit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                         dialog.dismiss();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                builder.show();
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
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

//        xAxisTextView.setText("x: " + String.valueOf(String.format("%.2f", sensorEvent.values[0])));
//        yAxisTextView.setText("y: " + String.valueOf(String.format("%.2f", sensorEvent.values[1])));
//        zAxisTextView.setText("z: " + String.valueOf(String.format("%.2f", sensorEvent.values[2])));

        xAxisTextView.setText("x: " + String.valueOf(filterAlpha[0]));
        yAxisTextView.setText("y: " + String.valueOf(filterAlpha[1]));
        zAxisTextView.setText("z: " + String.valueOf(filterAlpha[2]));

        if(sensorIsRun) {
            dataLastXValue += 1d;
            //since the data is filtered and sorted, the max is the last value
            filteredData = addSamples(sensorEvent.values)[2];
            mDataSeries.appendData(new DataPoint(dataLastXValue, ax), true, 40);

            if(dataTransferQueue.size() < dataTransferSize) {
                dataTransferQueue.offer(filteredData);
                dataTransferSum += filteredData;
            }

            if(dataTransferQueue.size() >= dataTransferSize) {
                dataTransferSum -= dataTransferQueue.poll();
                dataTransferQueue.offer(filteredData);
                dataTransferSum += filteredData;
                dataTransferMean = dataTransferSum / dataTransferSize;
                mDataTransferSeries.appendData(new DataPoint(dataLastXValue, dataTransferMean), true, 40);
//                System.out.println("Data Transfer: " + String.valueOf(dataLastXValue) + new ArrayList(dataTransferQueue));
            }

            if(dataThresholdQueue.size() < dataThresholdSize) {
                //while the threshold is being initialized, get proper alpha value for each axis
                calculateAlpha(sensorEvent.values);
                dataThresholdQueue.offer(filteredData);    //add new data to the queue
                dataThresholdSum += filteredData;          //add new data to the sum
            }

            if(dataThresholdQueue.size() >= dataThresholdSize){
                dataThresholdSum -= dataThresholdQueue.poll();    //remove the oldest data from the sum and the queue
                dataThresholdQueue.offer(filteredData);    //add new data to the queue
                dataThresholdSum += filteredData;          //add new data to the sum
                dataThresholdMean = dataThresholdSum / dataThresholdSize;  //calculate the new mean

                if(dataTransferMean > minimumPeak && dataTransferMean > peak) {
                    peak = dataTransferMean;
                    peakTextView.setText("Peak: " + String.valueOf(peak));
                }

                //multiply the mean with alpha to get the threshold
                thresholdAlpha = (dataThresholdMean * alphaValue)+ minValue;
                thresholdBeta = (dataThresholdMean * betaValue) + minValue;

                mAlphaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdAlpha), true, 40);
                mBetaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdBeta), true, 40);

                //if the data transfer pass beta, mark as start time
                if(!checkStartTime && !checkStopTime && (dataTransferMean > thresholdBeta)) {
                    checkStartTime = true;
                    startTime = dataLastXValue;
                    startTimeTextView.setText("Start Time: " + String.valueOf(startTime));
                //if there is an event counter, and the data transfer pass below alpha
                //mark as stop time, send the alert object to the database
                } else if(checkStartTime && eventTrigger && (dataTransferMean < thresholdAlpha)) {
                    checkStartTime = false;
                    checkStopTime = true;
                    stopTime = dataLastXValue;
                    stopTimeTextView.setText("Stop Time: " + String.valueOf(stopTime));
                    Alert alert = new Alert(startTime,stopTime, stopTime - startTime, new Date());
                    //update this document to alert the user
                    Device device = new Device(macAddress, alert);
                    deviceDocRef.set(device).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.i(TAG, "Send Alert");
                        }
                    });
                //if there is an event counted, and the data transfer is below the beta
                //reset stop time flag
                } else if (checkStopTime && (dataTransferMean < thresholdBeta)) {
                    checkStopTime = false;
                //if there is no event counted, and the data transfer is below the beta
                //reset all the flag
                } else if (!checkStopTime && (dataTransferMean < thresholdBeta)) {
                    checkStartTime = false;
                    eventTrigger = false;
                }

                //if the data transfer pass the alpha, mark an event
                if(!eventTrigger && (dataTransferMean > thresholdAlpha)) {
                    eventCounter += 1;
                    eventTrigger = true;
                    counterTextView.setText("Event Counter: " + String.valueOf(eventCounter));
                //if there is an event, and the data transfer is below alpha, reset the flag
                } else if (eventTrigger && (dataTransferMean < thresholdAlpha)) {
                    eventTrigger = false;
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
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
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

    public void calculateAlpha(float[] axisData) {
        for(int i = 0; i < axisData.length; i++) {
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
//
//        timestamp = System.nanoTime();
//
//        // Find the sample period (between updates).
//        // Convert from nanoseconds to seconds
//        dt = 1 / (count / ((timestamp - timestampOld) / 1000000000.0f));
//
//        count++;
//
//        filterAlpha = timeConstant / (timeConstant + dt);

        gravity[0] = filterAlpha[0] * gravity[0] + (1 - filterAlpha[0]) * input[0];
        gravity[1] = filterAlpha[1] * gravity[1] + (1 - filterAlpha[1]) * input[1];
        gravity[2] = filterAlpha[2] * gravity[2] + (1 - filterAlpha[2]) * input[2];

        //get the linear acceleration convert them to absolute values
        linearAcceleration[0] = Math.abs(input[0] - gravity[0]);
        linearAcceleration[1] = Math.abs(input[1] - gravity[1]);
        linearAcceleration[2] = Math.abs(input[2] - gravity[2]);
        //sort the arrays to get the max value
        Arrays.sort(linearAcceleration);

//        linearAcceleration[0] = input[0] - gravity[0];
//        linearAcceleration[1] = input[1] - gravity[1];
//        linearAcceleration[2] = input[2] - gravity[2];

        return linearAcceleration;
    }
}
