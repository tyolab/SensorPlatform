package mnefzger.de.sensorplatform;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;

import mnefzger.de.sensorplatform.Utilities.MathFunctions;


public class OrientationProvider extends SensorProvider {
    private Sensor rotationSensor;

    public OrientationProvider(Context c, SensorModule m) {
        super(c,m);
    }

    public void start() {

        super.start();
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null){
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        } else {
            Log.d("SENSOR", "TYPE_ROTATION_VECTOR not available on device");
        }

        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        super.stop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values.clone();

        float[] eulerValues = MathFunctions.calculateEulerAngles(values);

        // get the rotation matrix
        float[] temp_matrix = new float[9];
        SensorManager.getRotationMatrixFromVector(temp_matrix, values);


        float[][] result = new float[2][];
        result[0] = eulerValues;
        result[1] = temp_matrix;

        sensorCallback.onRotationData(result);
    }

}
