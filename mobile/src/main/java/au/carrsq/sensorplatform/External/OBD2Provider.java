package au.carrsq.sensorplatform.External;

import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.fuel.ConsumptionRateCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import au.carrsq.sensorplatform.Core.DataProvider;
import au.carrsq.sensorplatform.Core.ISensorCallback;
import au.carrsq.sensorplatform.Core.Preferences;
import au.carrsq.sensorplatform.R;


public class OBD2Provider extends DataProvider implements OBD2Connector.IConnectionEstablished{
    SharedPreferences setting_prefs;
    SharedPreferences sensor_prefs;
    ISensorCallback callback;
    private int OBD_DELAY;

    private boolean setupComplete = false;
    private boolean setupRunning = false;
    private boolean collecting = false;
    private Context app;

    private boolean tryingToReconnect = false;
    private int outOfBoundsCounter = 0;

    private final String TAG = "OBD_BLUETOOTH";


    public OBD2Provider(Context app, ISensorCallback callback) {
        this.callback = callback;
        this.app = app;

        setting_prefs = app.getSharedPreferences(app.getString(R.string.settings_preferences_key), Context.MODE_PRIVATE);
        sensor_prefs = app.getSharedPreferences(app.getString(R.string.sensor_preferences_key), Context.MODE_PRIVATE);
        OBD_DELAY = Preferences.getOBDDelay(setting_prefs);
    }

    public void connect() {
        if(Preferences.OBDActivated(sensor_prefs) && !OBD2Connection.connected) {
            reset();
        } else {
            app.sendBroadcast(new Intent("OBD_FOUND"));
            app.sendBroadcast(new Intent("OBD_CONNECTED"));
            app.sendBroadcast(new Intent("OBD_SETUP_COMPLETE"));
        }
    }

    @Override
    public void start() {
        collecting = true;
        collectOBDData();
    }

    @Override
    public void stop() {
        if(OBD2Connection.sock != null) {
            try {
                OBD2Connection.sock.getInputStream().close();
                OBD2Connection.sock.getOutputStream().close();
                OBD2Connection.sock.close();
                OBD2Connection.sock = null;
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

        collecting = false;
        OBD2Connection.connected = false;
        setupComplete = false;
    }

    /*
     * Sets up a new connection to the OBDII device.
     * Is called for initial setup and in case of IOException with broken pipe
     */
    public void reset() {
        Log.d(TAG, "reset");
        IntentFilter f = new IntentFilter();
        f.addAction("OBD_CONNECTED");
        f.addAction("OBD_NOT_FOUND");
        app.registerReceiver(mReceiver,f);

        tryingToReconnect = true;

        OBD2Connection.sock = null;
        OBD2Connection.connected = false;
        setupComplete = false;
        OBD2Connection.connector = new OBD2Connector(app, this);
    }

    @Override
    public void onConnectionEstablished() {
        setup();
    }

    /**
     * Runs the commands that set up the OBDII settings
     * https://github.com/pires/obd-java-api
     */
    private void setup() {
        setupRunning = true;
        // allow small wait for connection to be stable
        try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); }

        /* Setup */
        runCommand(OBD2Connection.sock, "ATD");
        runCommand(OBD2Connection.sock, "ATZ");
        runCommand(OBD2Connection.sock, "ATZ");
        runCommand(OBD2Connection.sock, "ATZ");
        runCommand(OBD2Connection.sock, "ATZ");
        runCommand(OBD2Connection.sock, "AT E0");
        runCommand(OBD2Connection.sock, "AT L0");
        runCommand(OBD2Connection.sock, "AT S0");
        runCommand(OBD2Connection.sock, "AT H0");

        // try to run a command
        SpeedCommand cmd = new SpeedCommand();
        runCommand(OBD2Connection.sock, cmd);

        RPMCommand rpm_cmd = new RPMCommand();
        runCommand(OBD2Connection.sock, rpm_cmd);

        app.sendBroadcast(new Intent("OBD_SETUP_COMPLETE"));

        setupComplete = true;
        setupRunning = false;
    }

    private void collectOBDData() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if(OBD2Connection.sock != null && OBD2Connection.sock.isConnected()) {  // connection is established, run commands
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if(setupComplete)
                                requestData();
                            else {
                                if(!setupRunning) setup();
                            }
                        }
                    }).start();

                } else if(collecting && !tryingToReconnect) {     // connection is lost, try reconnecting...
                    double[] resp = {-1,-1,-1};
                    callback.onOBD2Data(resp);
                    Log.d("OBD", "Connection lost, reconnecting.");
                    reset();
                }

                // if data collection is still running, call again
                if(collecting)
                    collectOBDData();
            }
        }, OBD_DELAY);
    }

    /*
     * Run the commands and report values back to callback.
     * Speed, Engine Speed, Fuel consumption
     */
    private void requestData() {
        SpeedCommand sp_cmd = new SpeedCommand();
        runCommand(OBD2Connection.sock, sp_cmd);

        RPMCommand rpm_cmd = new RPMCommand();
        runCommand(OBD2Connection.sock, rpm_cmd);

        ConsumptionRateCommand cr_cmd = new ConsumptionRateCommand();
        runCommand(OBD2Connection.sock, cr_cmd);

        try { Thread.sleep(10); } catch (InterruptedException e) { e.printStackTrace(); }

        double speed = Double.valueOf( sp_cmd.getCalculatedResult() );
        double rpm = Double.valueOf( rpm_cmd.getCalculatedResult() );
        double cr = Double.valueOf( cr_cmd.getCalculatedResult() );

        double[] response = {speed, rpm, cr};

        callback.onOBD2Data(response);
    }

    public void runCommand(BluetoothSocket s, String cmd) {
        if(OBD2Connection.obd2Device != null && s != null) {
            try{
                mRun(cmd, s.getInputStream(), s.getOutputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void runCommand(BluetoothSocket s, ObdCommand cmd) {
        if(OBD2Connection.obd2Device != null) {
            try {
                cmd.run(s.getInputStream(), s.getOutputStream());

            } catch (IOException io) {
                if (io.getMessage().contains("Broken pipe")) {
                    if (!tryingToReconnect) {
                        Log.d("OBD", "Broken pipe, reconnecting.");
                        double[] resp = {-1,-1,-1};
                        callback.onOBD2Data(resp);
                        reset();
                    }
                }

            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                outOfBoundsCounter += 1;
                if (!tryingToReconnect && outOfBoundsCounter >= 3) {
                    Log.d("OBD", "Out of bounds, reconnecting.");
                    double[] resp = {-1,-1,-1};
                    callback.onOBD2Data(resp);
                    outOfBoundsCounter = 0;
                    reset();

                }

            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void mRun(String cmd, InputStream in, OutputStream out) throws IOException,
            InterruptedException {
        Log.d("CMD", cmd);
        out.write((cmd + "\r").getBytes());
        out.flush();
        Thread.sleep(50);
        String raw = readRawData(in);
    }

    protected String readRawData(InputStream in) throws IOException {
        String rawData;
        byte b = 0;
        StringBuilder res = new StringBuilder();

        // read until '>' arrives OR end of stream reached
        char c;
        // -1 if the end of the stream is reached
        while (((b = (byte) in.read()) > -1)) {
            c = (char) b;
            if (c == '>') // read until '>' arrives
            {
                break;
            }
            res.append(c);
        }
        /*
         * Imagine the following response 41 0c 00 0d.
         *
         * ELM sends strings!! So, ELM puts spaces between each "byte". And pay
         * attention to the fact that I've put the word byte in quotes, because 41
         * is actually TWO bytes (two chars) in the socket. So, we must do some more
         * processing..
         */
        rawData = res.toString().replaceAll("SEARCHING", "");

        /*
         * Data may have echo or informative text like "INIT BUS..." or similar.
         * The response ends with two carriage return characters. So we need to take
         * everything from the last carriage return before those two (trimmed above).
         */
        //kills multiline.. rawData = rawData.substring(rawData.lastIndexOf(13) + 1);
        rawData = rawData.replaceAll("\\s", "");//removes all [ \t\n\x0B\f\r]

        return rawData;
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals("OBD_CONNECTED") ) {
                tryingToReconnect = false;
                app.unregisterReceiver(mReceiver);
            }

            if(action.equals("OBD_NOT_FOUND")) {
                if(!collecting)
                    app.unregisterReceiver(mReceiver);
                if(collecting)
                    reset();
            }
        }

    };


}