package mnefzger.de.sensorplatform;


public enum DataType {
    /**
     * If an application subscribes to this type, all available raw data is collected and reported
     */
    RAW,
    /**
     *
     */
    ACCELERATION_RAW,
    /**
     *
     */
    ACCELERATION_EVENT,
    /**
     *
     */
    ROTATION_RAW,
    /**
     *
     */
    ROTATION_EVENT,
    /**
     * includes lat, lon, current speed
     */
    LOCATION_RAW,
    /**
     * includes events of speeding
     */
    LOCATION_EVENT,
    /**
     *
     */
    CAMERA_RAW
}
