package au.carrsq.sensorplatform.Core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import au.carrsq.sensorplatform.External.OBD2Connection;
import au.carrsq.sensorplatform.R;

/**
 * Analyzes Datavectors in real-time to detect a trip end.
 * Trip end is detected ...
 * A. ... if the OBD engine speed == 0
 * B. ... if the vehicle speed < 1 for 30 seconds
 */
public class TripEndDetector {
    private SharedPreferences sensor_prefs;

    private boolean counting = false;
    private Long firstStop = null;
    private int count = 0;

    private ITripDetectionCallback callback;

    public TripEndDetector(ITripDetectionCallback callback, Context c) {
        sensor_prefs = c.getSharedPreferences(c.getString(R.string.sensor_preferences_key), Context.MODE_PRIVATE);

        this.callback = callback;
    }


    public void checkForTripEnd(DataVector dv) {
        if(++count < 10)
            return;

        boolean obd_active = Preferences.OBDActivated(sensor_prefs);
        boolean location_active = Preferences.locationActivated(sensor_prefs);

        if(obd_active && OBD2Connection.sock != null && OBD2Connection.sock.isConnected())
            checkInOBD(dv);

        if(location_active && dv.lat != 0 && dv.lon != 0)
            checkInSpeed(dv);

    }

    private void checkInOBD(DataVector dv) {
        if(dv.rpm != null && dv.rpm == 0.0 && dv.obdSpeed == 0.0) {
            Log.d("TRIP END","Trip end in OBD");
            callback.onTripEnd();
        }
    }

    public void reset() {
        counting = false;
        firstStop = null;
        count = 0;
    }

    private void checkInSpeed(DataVector dv) {
        if(dv.speed < 1 && dv.speed >= 0) {
            if(!counting) {
                firstStop = dv.timestamp;
                counting = true;
            } else {
                long duration = dv.timestamp - firstStop;

                if(duration > 45000) {
                    Log.d("TRIP END","Trip end in Speed");
                    callback.onTripEnd();
                }
            }
        } else {
            reset();

        }
    }
}
