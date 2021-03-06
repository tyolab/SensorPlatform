package au.carrsq.sensorplatform.Utilities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;

/**
 * Bundles all permission request in one static function that can be called on startup.
 * Alternatively, single permissions can be requested during runtime.
 */
public class PermissionManager {

    public static void verifyPermissions(Context c) {
        verifyAllPermissions(c);
        /*
        verifyStoragePermissions(c);
        verifyInternetPermission(c);
        verifyLocationPermissions(c);
        verifyBluetoothPermissions(c);
        verifyCameraPermissions(c);
        */
    }

    private static final int REQUEST_LOCATION = 1;
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param context
     */
    private static void verifyLocationPermissions(Context context) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions((Activity)context,
                    PERMISSIONS_LOCATION, REQUEST_LOCATION);
        }
    }


    private static final int REQUEST_CAMERA = 2;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
    };
    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param context
     */
    private static void verifyCameraPermissions(Context context) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions((Activity)context,
                    PERMISSIONS_CAMERA, REQUEST_CAMERA);
        }
    }



    private static final int REQUEST_INTERNET = 3;
    private static String[] PERMISSIONS_INTERNET = {
            Manifest.permission.INTERNET
    };
    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param context
     */
    private static void verifyInternetPermission(Context context) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.INTERNET);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions((Activity)context,
                    PERMISSIONS_INTERNET, REQUEST_INTERNET);
        }
    }



    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 4;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param context
     */
    private static void verifyStoragePermissions(Context context) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    (Activity)context,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private static final int USE_BLUETOOTH = 5;
    private static String[] PERMISSION_BLUETOOTH = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
    };
    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param context
     */
    public static void verifyBluetoothPermissions(Context context) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions((Activity)context,
                    PERMISSION_BLUETOOTH, USE_BLUETOOTH);
        }
    }


    // Storage Permissions
    private static final int REQUEST_ALL = 6;
    private static String[] PERMISSIONS_ALL = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NFC
    };

    private static void verifyAllPermissions(Context context) {
        ActivityCompat.requestPermissions(
                (Activity)context,
                PERMISSIONS_ALL,
                REQUEST_ALL
        );
    }
}

