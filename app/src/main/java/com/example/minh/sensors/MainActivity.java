package com.example.minh.sensors;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "Main Activity";

    private FirebaseDatabase mFirebaseDatabase;         //an instance for Firebase Database
    private DatabaseReference mAlertDatabaseReference;       //an instance for the database listener
    private ChildEventListener mAlertChildEventListener;     //an instance for the child listener in the database
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
//    private Date startTime;
//    private Date stopTime;
    private double startTime, stopTime;
    private boolean checkTime = false;

    private SensorManager sensorManager;
    private Sensor sensor;
    private TextView dataTextView, thresholdTextView, startTimeTextView, stopTimeTextView;
    private Button runButton;
    private float ax, absoluteAx, thresholdAlpha, thresholdBeta;
    private Queue<Float> dataQueue = new LinkedList<>();
    private float dataSum = 0.0f;
    private float dataMean = 0.0f;
    private static final int dataSize  = 100;
    private static final float alphaValue = 4;
    private static final float betaValue = 2;
    private static final float minValue = 0.03f;
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

    private boolean upState = false;
    private boolean upState2 = false;
    private boolean upState3 = false;
    private boolean downState = false;
    private boolean downState2 = false;
    private boolean downState3 = false;
    private int eventCounter = 0;

    private final Handler mHandler = new Handler();
    private LineGraphSeries<DataPoint> mDataSeries;
    private LineGraphSeries<DataPoint> mUpperAlphaThresholdSeries;
    private LineGraphSeries<DataPoint> mUpperBetaThresholdSeries;
    private LineGraphSeries<DataPoint> mLowerAlphaThresholdSeries;
    private LineGraphSeries<DataPoint> mLowerBetaThresholdSeries;

    private double dataLastXValue = 5d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mAlertDatabaseReference = mFirebaseDatabase.getReference().child("Alerts");

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
        mUpperAlphaThresholdSeries = new LineGraphSeries<>();
        mUpperAlphaThresholdSeries.setColor(Color.RED);

        mLowerAlphaThresholdSeries = new LineGraphSeries<>();
        mLowerAlphaThresholdSeries.setColor(Color.RED);

        mUpperBetaThresholdSeries = new LineGraphSeries<>();
        mUpperBetaThresholdSeries.setColor(Color.YELLOW);

        mLowerBetaThresholdSeries = new LineGraphSeries<>();
        mLowerBetaThresholdSeries.setColor(Color.YELLOW);

        graph.addSeries(mDataSeries);
        graph.addSeries(mUpperAlphaThresholdSeries);
        graph.addSeries(mUpperBetaThresholdSeries);
        graph.addSeries(mLowerAlphaThresholdSeries);
        graph.addSeries(mLowerBetaThresholdSeries);
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
//            dataTextView.setText("Filtered Data: " + String.valueOf(ax));
            dataLastXValue += 1d;
            mDataSeries.appendData(new DataPoint(dataLastXValue, ax), true, 40);

            if(dataQueue.size() < dataSize) {
                dataQueue.offer(absoluteAx);    //add new data to the queue
                dataSum += absoluteAx;          //add new data to the sum
            }
            if(dataQueue.size() >= dataSize){
                dataSum -= dataQueue.poll();    //remove the oldest data from the sum and the queue
                dataQueue.offer(absoluteAx);    //add new data to the queue
                dataSum += absoluteAx;          //add new data to the sum
                dataMean = dataSum / dataSize;  //calculate the new mean

                //multiply the mean with alpha to get the threshold
                thresholdAlpha = (dataMean * alphaValue) + (minValue * 2);
                thresholdBeta = (dataMean * betaValue) + minValue;
                thresholdTextView.setText("Threshold: " + String.valueOf(thresholdAlpha));

                mUpperAlphaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdAlpha), true, 40);
                mUpperBetaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdBeta), true, 40);
                mLowerAlphaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdAlpha * -1), true, 40);
                mLowerBetaThresholdSeries.appendData(new DataPoint(dataLastXValue, thresholdBeta * -1), true, 40);
            }

            if(dataQueue.size() >= dataSize) {
                if(!upState && !downState && !checkTime) {
                    if((ax > thresholdBeta) || (ax < (thresholdBeta * -1))) {
                        startTime = dataLastXValue;
                        startTimeTextView.setText("Start Time: " + String.valueOf(startTime));
                        checkTime = true;
                    }
                }

                if (upState) {
                    if(upState3) {
                        if(ax < minValue) {
                            eventCounter += 1;
                            upState = upState2 = upState3 = checkTime = false;
                            dataTextView.setText("Event Counter: " + String.valueOf(eventCounter));
                            stopTime = dataLastXValue;
                            stopTimeTextView.setText("Stop Time: " + String.valueOf(stopTime));
                        }
                    }

                    if(upState2) {
                        if(ax > thresholdBeta) {
                            upState3 = true;
                            return;
                        }
                    }
                    if (ax < thresholdBeta) {
                        upState2 = true;
                    }
                    downState = false;
                    return;
                }

                if (downState) {

                    if(downState3) {
                        if (ax > (minValue * -1)) {
                            eventCounter += 1;
                            downState = downState2 = downState3 = checkTime = false;
                            dataTextView.setText("Event Counter: " + String.valueOf(eventCounter));
                            stopTime = dataLastXValue;
                            stopTimeTextView.setText("Stop Time: " + String.valueOf(stopTime));
                        }
                    }

                    if(downState2) {
                        if (ax < (thresholdBeta * -1)) {
                            downState3 = true;
                            return;
                        }
                    }

                    if (ax > (thresholdBeta * -1)) {
                        downState2 = true;
                    }
                    upState = false;
                    return;
                }

                if (ax > thresholdAlpha) {
                    upState = true;
                } else if (ax < (thresholdAlpha * -1)) {
                    downState = true;
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
