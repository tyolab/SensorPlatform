package mnefzger.de.sensorplatform;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mnefzger.de.sensorplatform.Utilities.SequenceEncoderWrapper;


public class ImageModule implements IEventCallback{
    private Context context;
    protected ImageProcessor imgProc;
    private IDataCallback callback;

    private CameraManager cameraManager;
    private CameraDevice camera_front;
    private CameraDevice camera_back;
    private CameraCaptureSession session_front;
    private CameraCaptureSession session_back;
    private ImageReader imageReader_front;
    private ImageReader imageReader_back;

    private List<YuvImage> backImages = new ArrayList<>();
    private List<YuvImage> frontImages = new ArrayList<>();

    private int FRONT_MAX_FPS = 15;
    private double FRONT_AVG_FPS = FRONT_MAX_FPS;
    private int FRONT_PROCESSING_FPS = 5;

    private int BACK_MAX_FPS = 15;
    private double BACK_AVG_FPS  = BACK_MAX_FPS;
    private int BACK_PROCESSING_FPS = 5;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private final String TAG = "CAMERA_SENSORPLATFORM";

    boolean saving = false;


    public ImageModule(SensorPlatformController controller, Activity app) {
        verifyCameraPermissions(app);
        context = app;
        callback = controller;
        cameraManager = (CameraManager) app.getSystemService(Activity.CAMERA_SERVICE);
        imgProc = new ImageProcessor(this);
    }

    public void startCapture() {
        startBackgroundThread();
        //open("0");
        open("1");
    }

    public void stopCapture() {
        stopBackgroundThread();
        camera_front.close();
        camera_back.close();
    }

    private void open(String id) {
        try {
            int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
            if(permission == PackageManager.PERMISSION_GRANTED) {
                if(id == "0") {
                    cameraManager.openCamera(id, backCameraStateCallback, null );
                    imageReader_back = ImageReader.newInstance(480, 640, ImageFormat.YUV_420_888, 15);
                    imageReader_back.setOnImageAvailableListener(onBackImageAvailableListener, mBackgroundHandler);
                }
                if(id == "1") {
                    cameraManager.openCamera(id, frontCameraStateCallback, null );
                    imageReader_front = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 15);
                    imageReader_front.setOnImageAvailableListener(onFrontImageAvailableListener, mBackgroundHandler);
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
            Log.i(TAG, camera.getId().toString());
            ImageModule.this.camera_back = camera;
            try {
                if(camera.getId() == "0") {
                    ImageModule.this.camera_back.createCaptureSession(Arrays.asList(imageReader_back.getSurface()), backSessionStateCallback, null);
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
            Log.i(TAG, camera.getId().toString());
            ImageModule.this.camera_front = camera;
            try {
                if(camera.getId() == "1") {
                    ImageModule.this.camera_front.createCaptureSession(Arrays.asList(imageReader_front.getSurface()), frontSessionStateCallback, null);
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


    private double lastFront = System.currentTimeMillis();
    private double lastFrontProc = System.currentTimeMillis();
    private double lastFrontSaved = System.currentTimeMillis();
    private void handleImageFront(Image img) {
        if(img != null) {
            byte[] bytes = getBytes(img);
            YuvImage yuvimage;

            double now = System.currentTimeMillis();

            /**
             * We do not want to process every frame
             */
            if(now - lastFrontProc >= (1000 / FRONT_PROCESSING_FPS) ) {
                byte[] processedImg = imgProc.processImage(bytes, img.getWidth(), img.getHeight());
                yuvimage = new YuvImage(processedImg, ImageFormat.NV21, img.getWidth(), img.getHeight(), null);
                lastFrontProc = now;
            } else {
                yuvimage = new YuvImage(bytes, ImageFormat.NV21, img.getWidth(), img.getHeight(), null);
            }

            /**
             * Store the received image (either processed or raw) and write it to file
             */
            if(now - lastFront >= (1000/(1+FRONT_MAX_FPS)) ) {
                double latestFPS = 1000 / (now - lastFront);
                FRONT_AVG_FPS = 0.9*FRONT_AVG_FPS + 0.1*latestFPS;

                frontImages.add(yuvimage);
                lastFront = now;

                //mBackgroundHandler.post( new ImageSaver(yuvimage, "front") );
            }

            /**
             * Save video every ten seconds to file
             */
            if(now - lastFrontSaved >= 10000 ) {
                //mBackgroundHandler.post( new VideoSaver(new ArrayList<YuvImage>(frontImages), (int)FRONT_AVG_FPS, "front") );
                new VideoSaver(new ArrayList<YuvImage>(frontImages), (int)FRONT_AVG_FPS, "front");
                lastFrontSaved = now;
            }

            /**
             * Only store the last ten seconds in the image buffer
             */
            if(frontImages.size() > (10*FRONT_MAX_FPS) ) {
                frontImages.remove(0);
            }

        }
        img.close();
    }

    private double lastBack = System.currentTimeMillis();
    private double lastBackProc = System.currentTimeMillis();
    private double lastBackSaved = System.currentTimeMillis();
    private void handleImageBack(Image img) {
        if(img != null) {
            byte[] bytes = getBytes(img);
            YuvImage yuvimage;

            double now = System.currentTimeMillis();


            /**
             * We do not want to process every frame
             */
            if(now - lastBackProc >= (1000 / BACK_PROCESSING_FPS) ) {
                byte[] processedImg = imgProc.processImage(bytes, img.getWidth(), img.getHeight());
                yuvimage = new YuvImage(processedImg, ImageFormat.NV21, img.getWidth(), img.getHeight(), null);
                lastFrontProc = now;
            } else {
                yuvimage = new YuvImage(bytes, ImageFormat.NV21, img.getWidth(), img.getHeight(), null);
            }

            /**
             * Store the received image (either processed or raw) and write it to file
             */
            if(now - lastBack >= (1000/(1+BACK_MAX_FPS)) ) {
                double latestFPS = 1000 / (now - lastBack);
                BACK_AVG_FPS = 0.9*BACK_AVG_FPS + 0.1*latestFPS;

                backImages.add(yuvimage);
                lastBack = now;

                //mBackgroundHandler.post( new ImageSaver(yuvimage, "front") );
            }

            /**
             * Save video every ten seconds to file
             */
            if(now - lastBackSaved >= 10000 ) {
                //mBackgroundHandler.post( new VideoSaver(new ArrayList<YuvImage>(frontImages), (int)FRONT_AVG_FPS, "front") );
                new VideoSaver(new ArrayList<YuvImage>(backImages), (int)BACK_AVG_FPS, "back");
                lastBackSaved = now;
            }

            /**
             * Only store the last ten seconds in the image buffer
             */
            if(backImages.size() > (10*BACK_MAX_FPS) ) {
                backImages.remove(0);
            }

           // mBackgroundHandler.post( new ImageSaver(yuvimage, "back") );
        }
        img.close();
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

    public static Bitmap getBitmapImageFromYUV(YuvImage img, int width, int height) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        img.compressToJpeg(new Rect(0, 0, width, height), 80, baos);

        byte[] jdata = baos.toByteArray();
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
        return bmp;
    }

    private static class VideoSaver {

        private ArrayList<YuvImage> images = new ArrayList<>();
        private int FPS;
        SequenceEncoderWrapper encoder;
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName = "Video-" + System.nanoTime() + ".mp4";
        String filePath = baseDir + "/SensorPlatform/videos/";
        File mFile;

        public VideoSaver(ArrayList<YuvImage> list, int FPS, String mode) {
            this.images = list;
            this.FPS = FPS;

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

            save();
        }

        private void save() {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Log.d("VIDEO", "Trying to save..." + images.size() + " frames");
                        double start = System.currentTimeMillis();
                        encoder = new SequenceEncoderWrapper(mFile, images.size(), FPS);
                        for (int i = 0; i < images.size(); i++) {
                            YuvImage image = images.get(i);
                            Bitmap bi = getBitmapImageFromYUV(image, image.getWidth(), image.getHeight());
                            encoder.encodeImage(bi);
                        }
                        encoder.finish();

                        double delta = (System.currentTimeMillis() - start)/1000;
                        Log.d("VIDEO", "Saving finished in " + delta + "s!" );

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
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



    private static final int REQUEST_CAMERA = 1;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
    };
    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param activity
     */
    public static void verifyCameraPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity,
                    PERMISSIONS_CAMERA, REQUEST_CAMERA);
        }
    }
}
