package au.carrsq.sensorplatform.Core;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.SparseArray;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;

import au.carrsq.sensorplatform.Processors.ImageProcessor;
import au.carrsq.sensorplatform.R;
import au.carrsq.sensorplatform.Utilities.ThreadPool;

/**
 * This class provides the connection to front and back camera.
 * It also includes functions to save in the background:
 * a. single images
 * b. a number of single images as video
 */

public class ImageModule implements IEventCallback{
    private Context context;
    protected ImageProcessor imgProc;
    private IDataCallback callback;
    private SharedPreferences setting_prefs;
    private SharedPreferences sensor_prefs;

    private CameraManager cameraManager;
    private CameraDevice camera_front;
    private CameraDevice camera_back;
    private CameraCaptureSession session_front;
    private CameraCaptureSession session_back;
    private ImageReader imageReader_front;
    private ImageReader imageReader_back;

    private SparseArray<YuvImage> backImages = new SparseArray<>();
    private SparseArray<YuvImage> frontImages = new SparseArray<>();
    private SparseArray<byte[]> frontImagesCV = new SparseArray<>();
    private SparseArray<byte[]> backImagesCV = new SparseArray<>();

    private double FRONT_AVG_FPS;
    private int FRONT_PROCESSING_FPS;

    private double BACK_AVG_FPS;
    private int BACK_PROCESSING_FPS;

    private int RES_W;
    private int RES_H;

    private int VIDEO_DURATION = 10;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Handler h;

    private final String TAG = "CAMERA_SENSORPLATFORM";

    static boolean backSaving = false;
    static boolean frontSaving = false;
    static boolean reverseOrientation = false;


    public ImageModule(IDataCallback caller, Context app) {
        context = app;
        callback = caller;
        cameraManager = (CameraManager) app.getSystemService(Activity.CAMERA_SERVICE);
        imgProc = new ImageProcessor(this, app);

        setting_prefs = app.getSharedPreferences(app.getString(R.string.settings_preferences_key), Context.MODE_PRIVATE);
        sensor_prefs = app.getSharedPreferences(app.getString(R.string.sensor_preferences_key), Context.MODE_PRIVATE);

        h = new Handler();

        try{
            CameraCharacteristics cc_b = cameraManager.getCameraCharacteristics("0");
            CameraCharacteristics cc_f = cameraManager.getCameraCharacteristics("1");
            int level_b = cc_b.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            int level_f = cc_f.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            Log.d("CAMERA", "LEVEL FRONT: " + level_f  + ", LEVEL BACK: " + level_b);
            if (level_f == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
                    level_b == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                Log.d("CAMERA", "Your smartphone camera does not support all of Sensor Platform's features");
                /*new AlertDialog.Builder(context)
                        .setTitle("Camera Support")
                        .setMessage("Your smartphone camera does not support all features.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // continue with delete
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();*/
            }
        } catch (CameraAccessException e) {
            Log.e("CAMERA", "Cannot access camera");
            e.printStackTrace();
        }

    }

    private void setPrefs() {
        FRONT_PROCESSING_FPS = Preferences.getFrontProcessingFPS(setting_prefs);
        FRONT_AVG_FPS = 30;

        BACK_PROCESSING_FPS = Preferences.getBackProcessingFPS(setting_prefs);
        BACK_AVG_FPS = 30;

        reverseOrientation = Preferences.isReverseOrientation(setting_prefs);
    }

    public void startCapture() {
        // set most recent settings
        setPrefs();

        // check again if cascade files are ready
        imgProc.persistFiles(context);

        // open cameras
        startBackgroundThread();
        if( Preferences.backCameraActivated(sensor_prefs) ) open("0");
        if( Preferences.frontCameraActivated(sensor_prefs) ) open("1");
    }

    public void stopCapture() {
        if(mBackgroundThread != null)
            stopBackgroundThread();

        ThreadPool.finish();

        if(camera_front != null) {
            frontImagesCV.clear();
            imageReader_front.close();
            camera_front.close();
            camera_front = null;
        }

        if(camera_back != null) {
            backImagesCV.clear();
            imageReader_back.close();
            camera_back.close();
            camera_back = null;
        }

    }

    private void open(String id) {
        RES_W = Preferences.getVideoResolution(setting_prefs);
        if(RES_W  == 1024) RES_H = 768;
        else if(RES_W == 640) RES_H = 480;
        else if(RES_W == 320) RES_H = 240;
        else {
            RES_W = context.getResources().getInteger(R.integer.video_resolution);
            RES_H = (int) (context.getResources().getInteger(R.integer.video_resolution) * 0.75);
        }

        Log.d("RESOLUTION", RES_W + "x" + RES_H);
        try {
            int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
            if(permission == PackageManager.PERMISSION_GRANTED) {
                if(id.equals("0")) {
                    imageReader_back = ImageReader.newInstance(RES_W, RES_H, ImageFormat.YUV_420_888, 15);
                    imageReader_back.setOnImageAvailableListener(onBackImageAvailableListener, mBackgroundHandler);
                    cameraManager.openCamera(id, backCameraStateCallback, null );
                }
                if(id.equals("1")) {
                    imageReader_front = ImageReader.newInstance(RES_W, RES_H, ImageFormat.YUV_420_888, 15);
                    imageReader_front.setOnImageAvailableListener(onFrontImageAvailableListener, mBackgroundHandler);
                    cameraManager.openCamera(id, frontCameraStateCallback, null );
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback backCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, camera.getId());
            ImageModule.this.camera_back = camera;
            try {
                if(camera.getId().equals("0")) {
                    ImageModule.this.camera_back.createCaptureSession(Collections.singletonList(imageReader_back.getSurface()), backSessionStateCallback, null);
                }
            } catch (CameraAccessException e){
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            stopCapture();
        }

        @Override
        public void onError(CameraDevice camera, int error) {}
    };

    private CameraDevice.StateCallback frontCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, camera.getId());
            ImageModule.this.camera_front = camera;
            try {
                if(camera.getId().equals("1") ) {
                    ImageModule.this.camera_front.createCaptureSession(Collections.singletonList(imageReader_front.getSurface()), frontSessionStateCallback, null);
                }
            } catch (CameraAccessException e){
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            stopCapture();
        }

        @Override
        public void onError(CameraDevice camera, int error) {}
    };

    private CameraCaptureSession.StateCallback frontSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            ImageModule.this.session_front = session;
            try {
                session_front.setRepeatingRequest(createCaptureRequest(camera_front, imageReader_front), null, null);
            } catch (CameraAccessException e){
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {}
    };

    private CameraCaptureSession.StateCallback backSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            ImageModule.this.session_back= session;
            try {
                session_back.setRepeatingRequest(createCaptureRequest(camera_back, imageReader_back), null, null);
            } catch (CameraAccessException e){
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {}
    };

    private ImageReader.OnImageAvailableListener onFrontImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader){
            handleImageFront(reader.acquireNextImage());
        }
    };

    private ImageReader.OnImageAvailableListener onBackImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader){
            handleImageBack(reader.acquireNextImage());
        }
    };

    private CaptureRequest createCaptureRequest(CameraDevice camera, ImageReader reader) {
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(reader.getSurface());
            return builder.build();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public void onEventDetected(EventVector v) {
        callback.onEventData(v);
    }

    @Override
    public void onEventDetectedWithoutTimestamp(EventVector v) {
        // empty, not in use
        Log.e("ImageModule", "Used onEventDetectedWithoutTimestamp(), not implemented");
    }

    public void saveVideoAfterEvent(EventVector ev) {
        final EventVector v = ev;
        if(true /*&& !isSaving()*/ ) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(Preferences.frontCameraActivated(sensor_prefs)) {
                        new VideoSaver(copyByteList(frontImagesCV), FRONT_AVG_FPS, RES_W, RES_H, "front", v.getTimestamp());
                    }
                    if(Preferences.backCameraActivated(sensor_prefs)) {
                        new VideoSaver(copyByteList(backImagesCV), BACK_AVG_FPS, RES_W, RES_H, "back", v.getTimestamp());
                    }
                }
            }, 4500);
        }
    }

    public boolean isSaving() {
        return (frontSaving || backSaving);
    }

    private double lastFront = System.currentTimeMillis();
    private double lastFrontProc = System.currentTimeMillis();
    private int frontIt = 0;
    private void handleImageFront(Image i) {
        double now = System.currentTimeMillis();
        //Log.d("IMAGE FORMAT", i.getFormat()+"");
        if(i != null) {
            final byte[] bytes = getBytes(i);
            final int w = i.getWidth();
            final int h = i.getHeight();
            i.close();
            //YuvImage yuvimage;

            /**
             * Decide if frame is to be processed or not
             */
            if(Preferences.frontImagesProcessingActivated(setting_prefs) && now - lastFrontProc >= (1000 / FRONT_PROCESSING_FPS) ) {
                if(mBackgroundThread == null) return;
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        imgProc.processImageFront(bytes, w, h);
                    }
                });
                /*byte[] processedImg = imgProc.processImageFront(bytes, img.getWidth(), img.getHeight());
                yuvimage = new YuvImage(processedImg, ImageFormat.NV21, img.getWidth(), img.getHeight(), null);
                mBackgroundHandler.post( new ImageSaver(yuvimage, "front") ); */
                lastFrontProc = now;
            }

            double latestFPS = 1000 / (now - lastFront);
            if(latestFPS > 5 && latestFPS < 40)
                FRONT_AVG_FPS = (FRONT_AVG_FPS + latestFPS)/2.0;

            frontImagesCV.append(frontIt, bytes);
            frontIt++;
            lastFront = now;

            /**
             * Only store the last ten seconds in the image buffer, 30FPS
             */
            if(frontImagesCV.size() > (VIDEO_DURATION * 30) ) {
                int key = frontImagesCV.keyAt(0);
                frontImagesCV.remove(key);
            }

        }
    }

    private double lastBack = System.currentTimeMillis();
    private double lastBackProc = System.currentTimeMillis();
    private int backIt = 0;
    private void handleImageBack(Image i) {
        double now = System.currentTimeMillis();

        if(i != null) {
            final byte[] bytes = getBytes(i);
            final int w = i.getWidth();
            final int h = i.getHeight();
            i.close();
            final YuvImage yuvimage;

            /**
             * Decide if frame is to be processed or not
             */
            if(Preferences.backImagesProcessingActivated(setting_prefs) && now - lastBackProc >= (1000 / BACK_PROCESSING_FPS) ) {
                if(mBackgroundThread == null) return;
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        imgProc.processImageBack(bytes, w, h);
                    }
                });
                /*byte[] processedImg = imgProc.processImageBack(bytes.clone(), w, h);
                yuvimage = new YuvImage(processedImg, ImageFormat.NV21, 320, 240, null);
                mBackgroundHandler.post( new ImageSaver(yuvimage, "back") );*/
                lastBackProc = now;
            }

            double latestFPS = 1000 / (now - lastBack);

            // filter out extreme values
            if(latestFPS > 5 && latestFPS < 40)
                BACK_AVG_FPS = (BACK_AVG_FPS + latestFPS)/2.0;

            //Log.d("FPS back", BACK_AVG_FPS+", " +latestFPS);

            backImagesCV.put(backIt, bytes);
            backIt++;
            lastBack = now;

            /**
             * Only store the last ten seconds in the image buffer, 30 FPS
             */
            if(backImagesCV.size() > (VIDEO_DURATION * 30) ) {
                int key = backImagesCV.keyAt(0);
                backImagesCV.remove(key);
            }

        }
    }

    private SparseArray<byte[]> copyByteList(SparseArray<byte[]> list) {
        SparseArray<byte[]> temp = new SparseArray<>();
        for(int i=0; i<list.size(); i++) {
            temp.put(i, list.valueAt(i));
        }
        return temp;
    }

    private byte[] getBytes(Image img) {
        ByteBuffer buffer0 = img.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = img.getPlanes()[2].getBuffer();
        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();

        byte[] bytes = new byte[buffer0_size + buffer2_size];
        buffer0.get(bytes, 0, buffer0_size);
        buffer2.get(bytes, buffer0_size, buffer2_size);

        return bytes;
    }

    public void updateSpeed(Double speed, Double obdSpeed) {
        if(Preferences.OBDActivated(sensor_prefs))
            imgProc.setCurrentSpeed(obdSpeed);
        else
            imgProc.setCurrentSpeed(speed);
    }

    private static class VideoSaver {
        private SparseArray<byte[]> images;
        private double FPS;
        private int w, h;
        private String mode;

        VideoWriter videoWriter;
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName;
        String filePath = baseDir + "/SensorPlatform/videos/";
        File mFile;

        public VideoSaver(SparseArray<byte[]> list, double FPS, int width, int height, String mode, long timestamp) {
            //this.images = copyByteList(list);
            this.images = list;
            this.FPS = FPS;
            this.w = width;
            this.h = height;
            this.mode = mode;

            fileName = mode + "-" + timestamp + ".avi";

            if(mode.equals("front") && frontSaving ) return;
            if(mode.equals("back") && backSaving ) return;

            File folder = new File(filePath);
            if (!folder.exists()) {
                folder.mkdir();
            }

            // subdirectory /front or /back
            filePath += mode;
            folder = new File(filePath);
            if (!folder.exists()) {
                folder.mkdir();
            }

            mFile = new File(filePath + File.separator + fileName);
            videoWriter = new VideoWriter(filePath + File.separator + fileName, VideoWriter.fourcc('M','J','P','G'), FPS, new Size(w,h));

            save();
        }

        private void save() {
            ThreadPool.post(new Runnable() {
                public void run() {
                    try {
                        if(mode.equals("front")) frontSaving = true;
                        if(mode.equals("back")) backSaving = true;
                        videoWriter.open(filePath + File.separator + fileName, VideoWriter.fourcc('M','J','P','G'), FPS, new Size(w,h));
                        Log.d("VIDEO", "Trying to save..." + images.size() + " frames, FPS " + FPS + ", path: " + filePath + File.separator + fileName + ", "+w+"x"+h);
                        double start = System.currentTimeMillis();
                        for (int i = 0; i < images.size(); i++) {
                            byte[] image = images.get(i);
                            Log.d("IMAGE LENGTH", image.length + "");
                            Mat imgMat = new Mat(h + h/2, w, CvType.CV_8UC1);
                            imgMat.put(0,0,image);

                            Mat rgbMat = new Mat(h,w,CvType.CV_8UC3);
                            Imgproc.cvtColor(imgMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV12);

                            // rotate the image 180 degrees if phone orientation requires it
                            if( (mode.equals("back") && reverseOrientation) ||
                                    (mode.equals("front") && !reverseOrientation ) )
                                Core.flip(rgbMat, rgbMat, -1);


                            videoWriter.write(rgbMat);
                            rgbMat.release();
                            imgMat.release();
                            Log.d("VIDEO", "save image " + rgbMat);
                        }
                        videoWriter.release();

                        double delta = (System.currentTimeMillis() - start)/1000;
                        Log.d("VIDEO", "Saving finished in " + delta + "s!" );
                        if(mode.equals("front")) frontSaving = false;
                        if(mode.equals("back")) backSaving = false;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        private SparseArray<byte[]> copyByteList(SparseArray<byte[]> list) {
            SparseArray<byte[]> temp = new SparseArray<>();
            for(int i=0; i<list.size(); i++) {
                temp.put(i, list.valueAt(i));
            }
            return temp;
        }


    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final YuvImage mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName = "IMG-" + System.nanoTime() + ".jpg";
        String filePath;

        public ImageSaver(YuvImage image, String path) {
            mImage = image;
            // image directory
            filePath = baseDir + "/SensorPlatform/images/";
            File folder = new File(filePath);
            if (!folder.exists()) {
                folder.mkdir();
            }
            // subdirectory /front or /back
            filePath += path;
            folder = new File(filePath);
            if (!folder.exists()) {
                 folder.mkdir();
            }

            mFile = new File(filePath + File.separator + fileName);
        }

        @Override
        public void run() {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            mImage.compressToJpeg(new Rect(0, 0, mImage.getWidth(),mImage.getHeight()), 100, baos);

            byte[] jpgData = baos.toByteArray();

            FileOutputStream output;
            try {
                if (!mFile.exists()) {
                    mFile.createNewFile();
                }
                output = new FileOutputStream(mFile);
                output.write(jpgData);
                output.getFD().sync();
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
