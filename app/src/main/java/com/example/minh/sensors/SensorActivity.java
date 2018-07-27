package com.example.minh.sensors;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

public class SensorActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "Sensor Activity";
    private static final String alertCollection = "alerts";
    private static final String historyCollection = "history" ;
    private FirebaseFirestore mFirestore;
    private DocumentReference alertDocRef;

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    //    private Date startTime;
//    private Date stopTime;
    private float startTime, stopTime;
    private boolean checkStartTime = false;
    private boolean checkStopTime = false;

    private SensorManager sensorManager;
    private Sensor sensor;
    private TextView dataTextView, thresholdTextView, startTimeTextView, stopTimeTextView;
    private Button runButton;
    private float ax, absoluteAx, thresholdAlpha, thresholdBeta;
    private Queue<Float> dataThresholdQueue = new LinkedList<>();
    private Queue<Float> dataTransferQueue = new LinkedList<>();
    private float dataThresholdSum = 0.0f;
    private float dataThresholdMean = 0.0f;
    private float dataTransferSum = 0.0f;
    private float dataTransferMean = 0.0f;
    private static final int dataThresholdSize = 100;
    private static final int dataTransferSize = 10;
    private static final float alphaValue = 2.5f;
    private static final float betaValue = 0.5f;
    private static final float minValue = 0.01f;
    private boolean sensorIsRun = false;

    //low pass filter
    private float timeConstant = 0.18f;
    private float filterAlpha = 0.1f;
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

    private final Handler mHandler = new Handler();
    private LineGraphSeries<DataPoint> mDataSeries;
    private LineGraphSeries<DataPoint> mDataTransferSeries;
    private LineGraphSeries<DataPoint> mAlphaThresholdSeries;
    private LineGraphSeries<DataPoint> mBetaThresholdSeries;

    private float dataLastXValue = 5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        mFirestore = FirebaseFirestore.getInstance();
        alertDocRef = mFirestore.collection(alertCollection).document("current_user");

        dataTextView = (TextView) findViewById(R.id.xAxisTextView);
        thresholdTextView = (TextView) findViewById(R.id.xAxisFilteredTextView);
        startTimeTextView = (TextView) findViewById(R.id.startTimeTextView);
        stopTimeTextView = (TextView) findViewById(R.id.stopTimeTextView);

        runButton = (Button) findViewById(R.id.runButton);
        runButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensorIsRun = !sensorIsRun;
                if(sensorIsRun) runButton.setText("Pause");
                else runButton.setText("Run");
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
        if(sensorIsRun) {

            ax = addSamples(sensorEvent.values)[0];
            absoluteAx = Math.abs(ax);
            dataLastXValue += 1d;
            mDataSeries.appendData(new DataPoint(dataLastXValue, ax), true, 40);

            if(dataTransferQueue.size() < dataTransferSize) {
                dataTransferQueue.offer(absoluteAx);
                dataTransferSum += absoluteAx;
            }

            if(dataTransferQueue.size() >= dataTransferSize) {
                dataTransferSum -= dataTransferQueue.poll();
                dataTransferQueue.offer(absoluteAx);
                dataTransferSum += absoluteAx;
                dataTransferMean = dataTransferSum / dataTransferSize;
                mDataTransferSeries.appendData(new DataPoint(dataLastXValue, dataTransferMean), true, 40);
                System.out.println("Data Transfer: " + String.valueOf(dataLastXValue) + new ArrayList(dataTransferQueue));
            }

            if(dataThresholdQueue.size() < dataThresholdSize) {
                dataThresholdQueue.offer(absoluteAx);    //add new data to the queue
                dataThresholdSum += absoluteAx;          //add new data to the sum
            }

            if(dataThresholdQueue.size() >= dataThresholdSize){
                dataThresholdSum -= dataThresholdQueue.poll();    //remove the oldest data from the sum and the queue
                dataThresholdQueue.offer(absoluteAx);    //add new data to the queue
                dataThresholdSum += absoluteAx;          //add new data to the sum
                dataThresholdMean = dataThresholdSum / dataThresholdSize;  //calculate the new mean

                //multiply the mean with alpha to get the threshold
                thresholdAlpha = (dataThresholdMean * alphaValue)+ minValue;
                thresholdBeta = (dataThresholdMean * betaValue) + minValue;
                thresholdTextView.setText("Threshold: " + String.valueOf(thresholdAlpha));

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
                    Alert alert = new Alert(startTime,stopTime, stopTime - startTime, dateFormat.format(new Date()));
                    //update this document to alert the user
                    alertDocRef.set(alert).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.i(TAG, "Send Alert");
                        }
                    });
                    //store the alert to the history
                    mFirestore.collection(historyCollection).add(alert)
                        .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                            @Override
                            public void onSuccess(DocumentReference documentReference) {
                                Log.i(TAG, "Save Alert");
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
                    dataTextView.setText("Event Counter: " + String.valueOf(eventCounter));
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
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    public float[] addSamples(float[] acceleration)
    {
        // Get a local copy of the sensor values
        System.arraycopy(acceleration, 0, this.input, 0, acceleration.length);

        timestamp = System.nanoTime();

        // Find the sample period (between updates).
        // Convert from nanoseconds to seconds
        dt = 1 / (count / ((timestamp - timestampOld) / 1000000000.0f));

        count++;

        filterAlpha = timeConstant / (timeConstant + dt);

        gravity[0] = filterAlpha * gravity[0] + (1 - filterAlpha) * input[0];
        gravity[1] = filterAlpha * gravity[1] + (1 - filterAlpha) * input[1];
        gravity[2] = filterAlpha * gravity[2] + (1 - filterAlpha) * input[2];

        linearAcceleration[0] = input[0] - gravity[0];
        linearAcceleration[1] = input[1] - gravity[1];
        linearAcceleration[2] = input[2] - gravity[2];

        return linearAcceleration;
    }
}
