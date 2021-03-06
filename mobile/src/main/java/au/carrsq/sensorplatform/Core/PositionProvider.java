package au.carrsq.sensorplatform.Core;


import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import au.carrsq.sensorplatform.R;
import au.carrsq.sensorplatform.Utilities.KalmanLatLong;
import au.carrsq.sensorplatform.Utilities.MathFunctions;

public class PositionProvider extends DataProvider implements LocationListener{
    private LocationManager locationManager;
    private Context context;
    private ISensorCallback callback;
    private Location lastLocation;
    private double lastTimestamp = System.currentTimeMillis();
    private List<Double> lastSpeedValues = new ArrayList<>();
    private KalmanLatLong kalman;

    SharedPreferences setting_prefs;

    private int LOCATION_REFRESH_TIME = 0;
    private final int LOCATION_REFRESH_DISTANCE = 0;

    public PositionProvider(Context app, ISensorCallback m) {
        context = app;
        callback = m;
        setting_prefs = app.getSharedPreferences(app.getString(R.string.settings_preferences_key), Context.MODE_PRIVATE);
        kalman = new KalmanLatLong(15);
    }

    @Override
    public void start() {
        LOCATION_REFRESH_TIME = Preferences.getGPSRequestRate(setting_prefs);

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME,
                    LOCATION_REFRESH_DISTANCE, this);
        }
    }

    @Override
    public void stop() {
        int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            if(locationManager != null)
                locationManager.removeUpdates(this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        if(kalman.get_TimeStamp() == 0)
            kalman.SetState(location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime());

        kalman.Process(location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime());
        double lat = kalman.get_lat();
        double lon = kalman.get_lng();

        location.setLatitude(lat);
        location.setLongitude(lon);

        double speed = calculateSpeed(location);

        callback.onLocationData(lat, lon, speed);
        h.removeCallbacksAndMessages(null);
        startTimeout();
    }

    Handler h = new Handler();
    private void startTimeout() {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(System.currentTimeMillis() - lastTimestamp > 10000)
                    callback.onLocationData(0, 0, -1);
            }
        }, 10);
    }

    private double calculateSpeed(Location location) {
        long currentTime = System.currentTimeMillis();
        double speed = -1;

        if(lastLocation != null) {
            //double distance = MathFunctions.calculateDistance(location.getLatitude(), location.getLongitude(), lastLocation.getLatitude(), lastLocation.getLongitude());
            double distance = location.distanceTo(lastLocation);
            // time between updates in seconds
            double timeDelta = (currentTime-lastTimestamp) / 1000.0;
            // speed in m/s
            speed = distance / timeDelta;
            // speed in km/h
            speed = speed * 3.6;

            lastSpeedValues.add(speed);
            if(lastSpeedValues.size() >= 3) {
                speed = MathFunctions.getAccEMASingle( lastSpeedValues, 0.75 );
                lastSpeedValues.set(lastSpeedValues.size()-1, speed);
            }

            if(lastSpeedValues.size() > 3)
                lastSpeedValues.remove(0);
        }

        lastLocation = location;
        lastTimestamp = currentTime;

        return speed;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }



}
