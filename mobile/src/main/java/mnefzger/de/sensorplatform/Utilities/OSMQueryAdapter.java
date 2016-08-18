package mnefzger.de.sensorplatform.Utilities;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;


public class OSMQueryAdapter {
    Context context;
    IOSMResponse callback;

    public OSMQueryAdapter(IOSMResponse caller, Context context) {
        this.context = context;
        this.callback = caller;
    }

    public void startSearchForRoad(Location location) {
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            double lat_w = location.getLatitude() - 0.001;
            double lon_s = location.getLongitude() - 0.001;
            double lat_e = location.getLatitude() + 0.001;
            double lon_n = location.getLongitude() + 0.001;

            //search(generateSearchStringBounding(lat_w, lon_s, lat_e, lon_n));
            search(generateSearchStringRadius(15, location.getLatitude(), location.getLongitude()), "Road");
        }
    }

    public void startSearchForSpeedLimit(Location location) {
        search(generateSearchStringSpeed(300, location.getLatitude(), location.getLongitude()), "Speed");
    }

    private String generateSearchStringBounding(double lat_w, double lon_s, double lat_e, double lon_n) {
        String url ="http://overpass-api.de/api/interpreter?data=[out:json][timeout:5];";
        url += "(way";
        url += "[\"highway\"~\"^primary|secondary|tertiary|residential|service\"]";
        url += "[\"name\"]";
        url += "("+ lat_w +"," + lon_s + "," + lat_e + "," + lon_n + ");";
        url += " <;);out body;";

        return url;
    }

    private String generateSearchStringRadius(double rad, double lat, double lon) {
        String url ="http://overpass-api.de/api/interpreter?data=[out:json][timeout:15];";
        url += "(way";
        url += "[\"highway\"~\"^primary|secondary|tertiary|residential|service\"]";
        url += "[\"name\"]";
        url += "(around:"+ rad +"," + lat + "," + lon + ");";
        url += ">;);out body;";

        return url;
    }

    private String generateSearchStringSpeed(double rad, double lat, double lon) {
        String url ="http://overpass-api.de/api/interpreter?data=[out:json][timeout:15];";
        url += "(node";
        url += "[\"traffic_sign\"=\"maxspeed\"]";
        url += "(around:"+ rad +"," + lat + "," + lon + ");";
        url += ">;);out body;";

        return url;
    }

    private void search(String url, String m) {
        RequestQueue queue = Volley.newRequestQueue(context);
        final String mode = m;

        Log.d("VOLLEY", url);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // variable names can not have colons
                        response = response.replace("maxspeed:forward", "maxspeed_forward");
                        response = response.replace("maxspeed:backward", "maxspeed_backward");

                        Gson gson = new Gson();
                        OSMRespone osmR = gson.fromJson(response, OSMRespone.class);
                        if(mode == "Road") callback.onOSMRoadResponseReceived(osmR);
                        if(mode == "Speed") callback.onOSMSpeedResponseReceived(osmR);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("VOLLEY","That didn't work!");
            }
        });

        // Add the request to the RequestQueue
        queue.add(stringRequest);
    }



}