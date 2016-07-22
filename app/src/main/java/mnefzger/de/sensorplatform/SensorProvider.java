package mnefzger.de.sensorplatform;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;

public abstract class SensorProvider extends DataProvider implements SensorEventListener{
    private Context context;
    protected ISensorCallback sensorCallback;
    protected SharedPreferences prefs;

    protected SensorManager sensorManager;

    SensorProvider(Context c, SensorModule m) {
        this.context = c;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorCallback = m;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void start() {
        super.start();
    }

    public  void stop() {
        super.stop();
        sensorManager.unregisterListener(this);
    }
}
