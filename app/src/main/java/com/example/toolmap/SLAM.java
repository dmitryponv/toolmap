package com.example.toolmap;

import android.content.Context;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Net;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.features2d.MSER;
import org.opencv.features2d.ORB;
//import org.opencv.xfeatures2d.SURF;
import org.opencv.imgproc.Imgproc;

import org.opencv.features2d.DescriptorMatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.features2d.Features2d;

public class SLAM {
    private static List<String> classes;
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

    public SLAM(Context m_context){
        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void touchEvent(Mat mat, int x, int y)
    {
        int cols = mat.cols();
        int rows = mat.rows();
        if ((x < 0) || (y < 0) || (x > cols) || (y > rows))
            return;

        Rect touchedRect = new Rect();

        lock.lock();

        lock.unlock();
    }

    //private static boolean switch_image = false;
    private static Mat descriptors1 = new Mat();
    private static Mat descriptors2 = new Mat();
    private static Mat descriptors3 = new Mat();
    private static MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
    private static MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
    private static MatOfKeyPoint keypoints3 = new MatOfKeyPoint();

    private static int frame = 0;
    public static Mat process(Mat img){
        lock.lock();
        frame++;

        ORB detector = ORB.create();
        if(frame==20) {
            detector.detect(img, keypoints1, new Mat());
            //switch_image = true;
            detector.detectAndCompute(img, new Mat(), keypoints1, descriptors1);
            return img;
        }
        else if(frame==21)
        {
            detector.detect(img, keypoints2, new Mat());
            //switch_image = true;
            detector.detectAndCompute(img, new Mat(), keypoints2, descriptors2);
        }
        else if(frame==22)
        {
            detector.detect(img, keypoints3, new Mat());
            //switch_image = true;
            detector.detectAndCompute(img, new Mat(), keypoints3, descriptors3);
        }
        else
        {
            return img;
        }

        descriptors1.convertTo(descriptors1, CvType.CV_32F);
        descriptors2.convertTo(descriptors2, CvType.CV_32F);
        descriptors3.convertTo(descriptors3, CvType.CV_32F);

        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        List<MatOfDMatch> knnMatches1 = new ArrayList<>();
        matcher.knnMatch(descriptors1, descriptors2, knnMatches1, 2);

        List<MatOfDMatch> knnMatches2 = new ArrayList<>();
        matcher.knnMatch(descriptors1, descriptors3, knnMatches2, 2)
        //-- Filter matches using the Lowe's ratio test
        float ratioThresh = 0.7f;
        List<DMatch> listOfGoodMatches1 = new ArrayList<>();
        for (int i = 0; i < knnMatches1.size(); i++) {
            if (knnMatches1.get(i).rows() > 1) {
                DMatch[] matches = knnMatches1.get(i).toArray();
                if (matches[0].distance < ratioThresh * matches[1].distance) {
                    listOfGoodMatches1.add(matches[0]);
                }
            }
        }
        List<DMatch> listOfGoodMatches2 = new ArrayList<>();
        for (int i = 0; i < knnMatches2.size(); i++) {
            if (knnMatches2.get(i).rows() > 1) {
                DMatch[] matches = knnMatches2.get(i).toArray();
                if (matches[0].distance < ratioThresh * matches[1].distance) {
                    listOfGoodMatches2.add(matches[0]);
                }
            }
        }

        //List<DMatch> matchesList = matches.toList();
        List<KeyPoint> kp1List = keypoints1.toList();
        List<KeyPoint> kp2List = keypoints2.toList();
        List<KeyPoint> kp3List = keypoints3.toList();

        //-- Filter matches using the Lowe's ratio test
        for (int i = 0; i < knnMatches.size(); i++) {
            if (knnMatches.get(i).rows() > 1) {
                DMatch[] matches = knnMatches.get(i).toArray();
                if (matches[0].distance < ratioThresh * matches[1].distance) {
                    listOfGoodMatches.add(matches[0]);
                }
            }
        }

        System.out.println("List for frame: " + frame);
        for(DMatch match : listOfGoodMatches)
        {
            //KeyPoint p1 = switch_image?  kp1List.get(match.trainIdx) : kp2List.get(match.trainIdx);
            //KeyPoint p2 = switch_image?  kp2List.get(match.queryIdx) : kp1List.get(match.queryIdx);
            KeyPoint p1 = kp2List.get(match.trainIdx);
            KeyPoint p2 = kp1List.get(match.queryIdx);

            System.out.println(p1.pt.x + "\t" + p1.pt.y + "\t" + p2.pt.x + "\t" + p2.pt.y);
        }
        //DescriptorExtractor descriptor = DescriptorExtractor.create(DescriptorExtractor.ORB);


        Mat output=new Mat();
        Features2d.drawKeypoints(img, keypoints1,output );

        lock.unlock();

        return output;
    }
}
