package com.example.toolmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;

import static android.util.Half.EPSILON;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Point3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Motion extends Activity implements SensorEventListener {
    private static final Lock lock = new ReentrantLock(true);
    //Float azimut;  // View to draw a compass

    private final int CNT = 4;
    private final float[] accelerometerReading = new float[3];
    private final float[][] magnetometerReadingTemp = new float[CNT][3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix  = new float[9];
    private final float[] orientationAngles = new float[3];

    public class CustomDrawableView extends View {
        Paint paint = new Paint();
        public CustomDrawableView(Context context) {
            super(context);
            paint.setColor(0xff00ff00);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setAntiAlias(true);
        };

        protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            int centerx = width/2;
            int centery = height/2;
            Point ground_1 = new Point(100, 100);
            Point ground_2 = new Point(100, -100);
            Point ground_3 = new Point(-100, -100);
            Point ground_4 = new Point(-100, 100);

            Point3 ground_1_tr = new Point3(
                    rotationMatrix[0]*ground_1.x+rotationMatrix[1]*ground_1.y+rotationMatrix[2]*1,//+I[0],
                    rotationMatrix[3]*ground_1.x+rotationMatrix[4]*ground_1.y+rotationMatrix[5]*1,//+I[1],
                    rotationMatrix[6]*ground_1.x+rotationMatrix[7]*ground_1.y+rotationMatrix[8]*1);//+I[2]);
            Point3 ground_2_tr = new Point3(
                    rotationMatrix[0]*ground_2.x+rotationMatrix[1]*ground_2.y+rotationMatrix[2]*1,//+I[0],
                    rotationMatrix[3]*ground_2.x+rotationMatrix[4]*ground_2.y+rotationMatrix[5]*1,//+I[1],
                    rotationMatrix[6]*ground_2.x+rotationMatrix[7]*ground_2.y+rotationMatrix[8]*1);//+I[2]);
            Point3 ground_3_tr = new Point3(
                    rotationMatrix[0]*ground_3.x+rotationMatrix[1]*ground_3.y+rotationMatrix[2]*1,//+I[0],
                    rotationMatrix[3]*ground_3.x+rotationMatrix[4]*ground_3.y+rotationMatrix[5]*1,//+I[1],
                    rotationMatrix[6]*ground_3.x+rotationMatrix[7]*ground_3.y+rotationMatrix[8]*1);//+I[2]);
            Point3 ground_4_tr = new Point3(
                    rotationMatrix[0]*ground_4.x+rotationMatrix[1]*ground_4.y+rotationMatrix[2]*1,//+I[0],
                    rotationMatrix[3]*ground_4.x+rotationMatrix[4]*ground_4.y+rotationMatrix[5]*1,//+I[1],
                    rotationMatrix[6]*ground_4.x+rotationMatrix[7]*ground_4.y+rotationMatrix[8]*1);//+I[2]);

            Point ground_1_pr = new Point(ground_1_tr.x + centerx, ground_1_tr.y + centery);
            Point ground_2_pr = new Point(ground_2_tr.x + centerx, ground_2_tr.y + centery);
            Point ground_3_pr = new Point(ground_3_tr.x + centerx, ground_3_tr.y + centery);
            Point ground_4_pr = new Point(ground_4_tr.x + centerx, ground_4_tr.y + centery);

            paint.setColor(0xff0000ff);
            canvas.drawLine((float)ground_1_pr.x, (float)ground_1_pr.y, (float)ground_2_pr.x, (float)ground_2_pr.y, paint);
            canvas.drawLine((float)ground_2_pr.x, (float)ground_2_pr.y, (float)ground_3_pr.x, (float)ground_3_pr.y, paint);
            canvas.drawLine((float)ground_3_pr.x, (float)ground_3_pr.y, (float)ground_4_pr.x, (float)ground_4_pr.y, paint);
            paint.setColor(0xff00ff00);
            canvas.drawLine((float)ground_4_pr.x, (float)ground_4_pr.y, (float)ground_1_pr.x, (float)ground_1_pr.y, paint);
        }
    }

    CustomDrawableView mCustomDrawableView;
    private SensorManager sensorManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCustomDrawableView = new CustomDrawableView(this);
        setContentView(mCustomDrawableView);    // Register the sensor listeners
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    protected void onResume() {
        super.onResume();

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    protected void onPause() {
        super.onPause();
        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {  }

    float[] mGravity;
    float[] mGeomagnetic;
    int cnt = 0;
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReadingTemp[cnt%CNT],
                    0, magnetometerReadingTemp[cnt%CNT].length);
            cnt++;

            if(cnt%CNT == 0)
            {
                float[][] output = getOutliers(magnetometerReadingTemp);
                int t = 0;

                for(int i = 0; i < output.length; i++) {
                    //avg -= avg / N;
                    //avg += new_sample / N;
                    int nCNT = output.length;
                    if(Math.abs(magnetometerReading[0]-output[i][0]) < 1) {
                        magnetometerReading[0] -= magnetometerReading[0] / nCNT;
                        magnetometerReading[0] += output[i][0] / nCNT;
                        magnetometerReading[1] -= magnetometerReading[1] / nCNT;
                        magnetometerReading[1] += output[i][1] / nCNT;
                        magnetometerReading[2] -= magnetometerReading[2] / nCNT;
                        magnetometerReading[2] += output[i][2] / nCNT;
                    }
                }
            }
        }

        updateOrientationAngles();
    }
    public float[][] getOutliers(float[][] input_array) {
        List<float[]> output = new ArrayList<float[]>();
        float[][] data1 = new float[5][3];
        float[][] data2 = new float[5][3];
        int len = input_array.length;
        if (input_array.length % 2 == 0) {
            data1 = Arrays.copyOfRange(input_array, 0, len / 2);
            data2 = Arrays.copyOfRange(input_array, len/2, len);
        } else {
            data1 = Arrays.copyOfRange(input_array, 0, len / 2);
            data2 = Arrays.copyOfRange(input_array, len/2+1, len);
        }
        float[] q1 = getMedian(data1);
        float[] q3 = getMedian(data2);
        float[] iqr = new float[3];
        float[] lowerFence = new float[3];
        float[] upperFence = new float[3];
        for(int i = 0 ; i < 3; i++) {
            iqr[i] = q3[i] - q1[i];
            lowerFence[i] = q1[i] - 1.5f * iqr[i];
            upperFence[i] = q3[i] + 1.5f * iqr[i];

        }

        for (int j = 0; j < len; j++) {
            if (input_array[j][0] < lowerFence[0] || input_array[j][0] > upperFence[0])
                if (input_array[j][1] < lowerFence[1] || input_array[j][1] > upperFence[1])
                    if (input_array[j][2] < lowerFence[2] || input_array[j][2] > upperFence[2])
                        output.add(input_array[j]);
        }

        return output.toArray(new float[output.size()][3]);
    }

    private static float[] getMedian( float[][] data) {
        int len = data.length;
        float[] median = new float[3];
        if (len % 2 == 0)
            for(int i = 0 ; i < 3; i++)
                median[i] = (data[len / 2][i] + data[len / 2][i]) / 2;
        else
            for(int i = 0 ; i < 3; i++)
                median[i] = data[len / 2][i];

        return median;
    }
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);

        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        // "orientationAngles" now has up-to-date information.

        mCustomDrawableView.invalidate();
    }

    public float[] invert3x3(float in_mat[]) {
        int i, j;
        float det = 0;
        float mat[][] = new float[3][3];
        //System.out.println("Enter elements of matrix row wise:");
        for (i = 0; i < 3; ++i)
            for (j = 0; j < 3; ++j)
                mat[i][j] = in_mat[j + 3 * i];
        for (i = 0; i < 3; i++)
            det = det + (mat[0][i] * (mat[1][(i + 1) % 3] * mat[2][(i + 2) % 3] - mat[1][(i + 2) % 3] * mat[2][(i + 1) % 3]));
        //System.out.println("\ndeterminant = " + det);
        //System.out.println("\nInverse of matrix is:");
        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 3; ++j)
                in_mat[j+3*i] = (((mat[(j + 1) % 3][(i + 1) % 3] * mat[(j + 2) % 3][(i + 2) % 3]) - (mat[(j + 1) % 3][(i + 2) % 3] * mat[(j + 2) % 3][(i + 1) % 3])) / det);
                //System.out.print((((mat[(j + 1) % 3][(i + 1) % 3] * mat[(j + 2) % 3][(i + 2) % 3]) - (mat[(j + 1) % 3][(i + 2) % 3] * mat[(j + 2) % 3][(i + 1) % 3])) / det) + " ");
            //System.out.print("\n");
        }
        return in_mat;
    }

    public static Mat process(Mat img) {
        lock.lock();
        lock.unlock();
        return img;
    }
}
