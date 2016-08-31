package mnefzger.de.sensorplatform.Core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import mnefzger.de.sensorplatform.External.OBD2Connection;
import mnefzger.de.sensorplatform.R;
import mnefzger.de.sensorplatform.UI.AppFragment;
import mnefzger.de.sensorplatform.UI.SettingsFragment;
import mnefzger.de.sensorplatform.UI.SetupFirstFragment;
import mnefzger.de.sensorplatform.UI.StartFragment;
import mnefzger.de.sensorplatform.Utilities.PermissionManager;


public class MainActivity extends AppCompatActivity implements IDataCallback{

    static {
        try {
            System.loadLibrary("opencv_java3");
            System.loadLibrary("imgProc");
        } catch (UnsatisfiedLinkError e) {
            Log.d("APPLICATION INIT", "Unsatisfied Link error: " + e.toString());
        }
    }

    StartFragment startFragment;
    SettingsFragment settings;
    AppFragment appFragment;
    SetupFirstFragment setupFragment;
    SharedPreferences prefs;
    SensorPlatformService sPS;
    boolean mBound = false;
    boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PermissionManager.verifyPermissions(this);

        //prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs = this.getSharedPreferences(getString(R.string.preferences_key), Context.MODE_PRIVATE);
        prefs.edit().clear();
        if(prefs.getAll().isEmpty()) {
            PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
            prefs.edit().commit();
        } else {
            PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        }

        if(savedInstanceState == null) {
            Log.d("CREATE", "New activity");
            // bind and start service running in the background
            Intent intent = new Intent(this, SensorPlatformService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            startService(intent);

            goToStartFragment(0);

        } else {
            // if the data collection was already started, set reference to the UI fragment that shows live data
            started = savedInstanceState.getBoolean("started");
            mBound = savedInstanceState.getBoolean("bound");

            if(SensorPlatformService.serviceRunning == false)
                started = false;

            Log.d("RECREATE", "started:"+started+", bound:"+mBound);
            if(started) {
                goToAppFragment();

            } else if(!mBound) {
                Log.d("BINDING", "Rebinding service");
                Intent intent = new Intent(this, SensorPlatformService.class);
                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

            }
        }

    }

    public void startMeasuring() {
        goToAppFragment();

        Intent startIntent = new Intent(this, SensorPlatformService.class);
        startIntent.setAction("SERVICE_DATA_START");
        startService(startIntent);
        started = true;

        sPS.subscribe();
    }

    @Override
    public void onRawData(DataVector v) {
        Log.d("RawData @ App  ", v.toString());
        if( appFragment != null && appFragment.isVisible())
            appFragment.updateUI(v);
    }

    @Override
    public void onEventData(EventVector v) {
        Log.d("EventData @ App  ", v.toString());
        if( appFragment != null && appFragment.isVisible())
            appFragment.updateUI(v);
    }

    IDataCallback getActivity() {
        return this;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get SensorPlatformService instance
            SensorPlatformService.LocalBinder binder = (SensorPlatformService.LocalBinder) service;
            sPS = binder.getService();
            mBound = true;
            sPS.setAppCallback(getActivity());
            Log.d("SERVICE", "is connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        Log.d("SAVE", "saving state...");
        savedInstanceState.putBoolean("started", started);
        savedInstanceState.putBoolean("bound", mBound);
    }

    @Override
    public void onPause() {
        super.onPause();

        // don't forget to unregister the receiver
        if(OBD2Connection.connector != null)
            OBD2Connection.connector.unregisterReceiver();

        if(mBound) {
            try {
                unbindService(mConnection);
                mBound = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void goToAppFragment() {
        if(started) {
            String frag = null;
            for(int i=1; i<getSupportFragmentManager().getBackStackEntryCount(); i++) {
                frag = getSupportFragmentManager().getBackStackEntryAt(getSupportFragmentManager().getBackStackEntryCount() - i).getName();
                Log.d("FRAGMENT", frag);
                if( frag.equals("mnefzger.de.sensorplatform.UI.AppFragment") ) {
                    appFragment = (AppFragment) getSupportFragmentManager().findFragmentByTag(frag);
                    break;
                }
            }
        } else {
            appFragment = new AppFragment();
        }

        changeFragment(appFragment, true, true);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

    }

    public void goToNewStudyFragment() {
        setupFragment = new SetupFirstFragment();
        changeFragment(setupFragment, true, true);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public void goToSettingsFragment() {
        settings = new SettingsFragment();
        android.app.FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.animator.slide_in_right_animator, R.animator.slide_out_left_animator);
        transaction.replace(R.id.fragment_container, settings).commit();
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    /**
     * Changing to the StartFragment allows for a short delay to allow the service to shut down completely
     */
    public void goToStartFragment(int ms) {
        startFragment = new StartFragment();

        Handler wait = new Handler();
        wait.postDelayed(new Runnable() {
            @Override
            public void run() {
                changeFragment(startFragment, true, true);
                MainActivity that = (MainActivity) getActivity();
                that.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }, ms);

    }

    private void changeFragment(Fragment frag, boolean saveInBackstack, boolean animate) {
        String backStateName = ((Object) frag).getClass().getName();

        try {
            FragmentManager manager = getSupportFragmentManager();
            boolean fragmentPopped = manager.popBackStackImmediate(backStateName, 0);

            if (!fragmentPopped && manager.findFragmentByTag(backStateName) == null) {
                //fragment not in back stack, create it.
                FragmentTransaction transaction = manager.beginTransaction();

                if (animate) {
                    transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
                }

                transaction.replace(R.id.fragment_container, frag, backStateName);

                if (saveInBackstack) {
                    transaction.addToBackStack(backStateName);
                } else {
                }

                transaction.commit();
            } else {
                // custom effect if fragment is already instanciated
            }
        } catch (IllegalStateException exception) {
            Log.w("FRAGMENT", "Unable to commit fragment, could be activity as been killed in background. " + exception.toString());
        }
    }

}