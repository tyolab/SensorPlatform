package mnefzger.de.sensorplatform;

import android.location.Location;

/**
 * Created by matthias on 20/06/16.
 */
public class DataVector {
    public long timestamp;
    /**
     * Three axis acceleration
     */
    public double accX;
    public double accY;
    public double accZ;
    /**
     * Three axis acceleration
     */
    public double rotX;
    public double rotY;
    public double rotZ;
    /**
     * vehicle location in lat, lon
     */
    public Location location;
    /**
     * vehicle speed in km/h
     */
    public double speed;

    public DataVector() {

    }

    public void setTimestamp(long time) {
        this.timestamp = time;
    }

    public void setAcc(double x, double y, double z) {
        this.accX = x;
        this.accY = y;
        this.accZ = z;
    }

    public void setRot(double x, double y, double z) {
        this.rotX = x;
        this.rotY = y;
        this.rotZ = z;
    }

    public void setLocation(Location l) {
        this.location = l;
    }

    public void setSpeed(double s) {
        this.speed = s;
    }

    @Override
    public String toString() {
        return "time: " + timestamp + ", accX: " + accX + ", accY: " + accY + ", accZ: " + accZ +
                ", rotX: " + rotX + ", rotY: " + rotY + ", rotZ: " + rotZ +", speed: " + speed;
    }

    public String toCSVString() {
        double lat = (location == null) ? 0 : location.getLatitude();
        double lon = (location == null) ? 0 : location.getLongitude();
        return timestamp + ";" + accX + ";" + accY + ";" + accZ + ";" +
                rotX + ";" + rotY + ";" + rotZ + ";" +
                lat + ";" + lon + ";" + speed;
    }
}
