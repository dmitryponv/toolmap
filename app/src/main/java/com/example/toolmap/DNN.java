package com.example.toolmap;

import static org.opencv.dnn.Dnn.readNetFromDarknet;

import org.opencv.core.*;
import org.opencv.dnn.DetectionModel;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.file.Files;
import java.io.File;
import android.os.Environment;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import android.content.Context;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import android.net.Uri;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DNN {
    private static  List<String> classes;
    private static Net net;

    static private MatOfInt classIds = new MatOfInt();
    static private MatOfFloat scores = new MatOfFloat();
    static private MatOfRect boxes = new MatOfRect();
    private static final Lock lock = new ReentrantLock(true);
    private static Mat t_mat = new Mat();;
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    public DNN(Context m_context){

        AssetManager assetManager = m_context.getAssets();
        try {
            InputStream ims1 = assetManager.open("coco.names");
            //InputStream ims2 = assetManager.open("yolov4-tiny.cfg");
            //InputStream ims3 = assetManager.open("yolov4-tiny.weights");
            //Path filePath1 =  getFileFromAssets(m_context, "yolov4-tiny.cfg").absolutePath;
            //Uri imageUri = Uri.fromFile(new File("android_asset/yolov4-tiny.cfg"));
            net = readNetFromDarknet(assetFilePath(m_context,"yolov4-tiny.cfg"), assetFilePath(m_context,"yolov4-tiny.weights"));

            classes = new BufferedReader(new InputStreamReader(ims1, StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
        }
        catch(IOException e) {
            e.printStackTrace();
        }

    }

    public static void touchEvent(Mat mat, int x, int y)
    {
        //Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        int cols = mat.cols();
        int rows = mat.rows();
        if ((x < 0) || (y < 0) || (x > cols) || (y > rows))
            return;

        Rect touchedRect = new Rect();

        lock.lock();
        boolean touched = false;
        for (int i = 0; i < classIds.rows(); i++) {
            Rect box = new Rect(boxes.get(i, 0));
            if(x>box.x && x < (box.x+box.width) && y>box.y && y < (box.y+box.height))
            {
                mat.submat(box).copyTo(t_mat);

                touched = true;
                break;
            }
        }
        if(!touched)
            t_mat = new Mat();



        //touchedRect.x = (x>4) ? x-4 : 0;
        //touchedRect.y = (y>4) ? y-4 : 0;

        //touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        //touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        //Mat touchedRegionHsv = new Mat();
        //Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        //// Calculate average color of touched region
        //mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        //int pointCount = touchedRect.width*touchedRect.height;
        //for (int i = 0; i < mBlobColorHsv.val.length; i++)
        //    mBlobColorHsv.val[i] /= pointCount;

        //mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        //Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
        //        ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        //mDetector.setHsvColor(mBlobColorHsv);

        //Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE, 0, 0, Imgproc.INTER_LINEAR_EXACT);

        //mIsColorSelected = true;

        //touchedRegionRgba.release();
        //touchedRegionHsv.release();
        lock.unlock();
    }
    public static Mat process(Mat img){
        DetectionModel model = new DetectionModel(net);
        model.setInputParams(1 / 255.0, new Size(416, 416), new Scalar(0), true);

        lock.lock();
        model.detect(img, classIds, scores, boxes, 0.3f, 0.2f);

        for (int i = 0; i < classIds.rows(); i++) {
            Rect box = new Rect(boxes.get(i, 0));
            Imgproc.rectangle(img, box, new Scalar(0, 255, 0), 2);

            int classId = (int) classIds.get(i, 0)[0];
            double score = scores.get(i, 0)[0];
            String text = String.format("%s: %.2f", classes.get(classId), score);
            Imgproc.putText(img, text, new Point(box.x, box.y - 5),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0), 2);

        }
        //Create an ROI overlay of the object
        Mat dst_roi = img.submat(new Rect(0, 0, t_mat.cols(), t_mat.rows()));
        t_mat.copyTo(dst_roi);


        lock.unlock();

        return img;
    }
}