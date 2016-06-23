package mnefzger.de.sensorplatform;

import android.app.Activity;
import android.hardware.Sensor;

import java.util.Iterator;

import mnefzger.de.sensorplatform.Logger.LoggingModule;

public class SensorPlatformController implements IDataCallback{

    private SensorModule sm;
    private LoggingModule lm;
    private IDataCallback appCallback;

    public SensorPlatformController(Activity app) {
        this.sm = new SensorModule(this, app);
        this.lm = new LoggingModule(app);
        this.appCallback = (IDataCallback) app;
    }

    public boolean subscribeTo(DataType type, boolean log) {
        /**
         * If a subscription with the same type already exists, return
         */
        Iterator<Subscription> it = ActiveSubscriptions.get().iterator();
        while(it.hasNext()) {
            if(it.next().getType() == type) {
                return false;
            }
        }

        Subscription s = new Subscription(type, log);

        switch (type) {
            case ACCELERATION_RAW:
                sm.startSensing(Sensor.TYPE_ACCELEROMETER);
                break;
            case ACCELERATION_EVENT:
                sm.startSensing(Sensor.TYPE_ACCELEROMETER);
                break;
            default:
                break;
        }

        ActiveSubscriptions.add(s);

        return true;
    }

    public boolean unsubscribe(DataType type) {
        Iterator<Subscription> it = ActiveSubscriptions.get().iterator();
        while(it.hasNext()) {
            Subscription sub = it.next();
            if(sub.getType() == type) {
                it.remove();
                ActiveSubscriptions.remove(sub);
                sm.StopSensing(type);
                return true;
            }
        }
        return false;
    }


    @Override
    public void onRawData(DataVector dv) {
        appCallback.onRawData(dv);
        if(ActiveSubscriptions.loggingActive()) {
            lm.writeRawToCSV(dv);
        }
    }

    @Override
    public void onEventData(EventVector ev) {
        appCallback.onEventData(ev);
    }



}
