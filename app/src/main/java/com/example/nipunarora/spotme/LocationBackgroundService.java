package com.example.nipunarora.spotme;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by nipunarora on 13/07/17.
 */

public class LocationBackgroundService extends Service {
    /*************** Initializing various parameters *************/
    private final IBinder mBinder = new MyBinder();
    private final String TAG="LocationBackService";
    Boolean mChangingConfiguration=false;
    private static final int NOTIFICATION_ID = 12345678;
    public String is_started_from_notification="isStartedFromNotification";
    LocationCallback location_callback;
    private FusedLocationProviderClient mFusedLocationClient;
    LocationRequest location_request;
    int iterative_key=0;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private NotificationManager notification_manager;
    Handler service_handler;
    String EXTRA_LOCATION="CurrentLocation";
    public String ACTION_BROADCAST="LocationBackgroundService.LocationUpdateBroadcast";
    Looper handler_thread_looper;
    Location previous_location=null;


    /*********************** Overrides ***********************/

    @Override
    public void onCreate() {
        super.onCreate();
        /******************** Initiate Service specific parameters ****************/
        createLocationRequest();
        createLocationCallback();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler_thread_looper=handlerThread.getLooper();
        service_handler = new Handler(handler_thread_looper);
        notification_manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mFusedLocationClient= LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        boolean startedFromNotification = intent.getBooleanExtra(is_started_from_notification,
                false);

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates();
            stopSelf();
        }else{
            requestLocationUpdates();
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "in onBind");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "in onRebind");
        try {
            stopForeground(true);
        }catch (Exception e){
            Log.i(TAG,"Stopping ForeGround: "+e.toString());
        }
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //We check that we arent fooled by a configuration change on the activity and we need a an update
        if(!mChangingConfiguration && isRequestingLocationUpdates()){
            Log.i(TAG, "Starting foreground service");
                startForeground(NOTIFICATION_ID, getNotification());
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration=true;
        //This method may be called if any configuration change occurs in the activity currently on the UI thread.
        //Thus we set the boolean as true so that we are not fooled into starting a foreground service due to onUnbind being called
    }

    @Override
    public void onDestroy() {
        service_handler.removeCallbacksAndMessages(null);
    }


    /***************** IBinder Class ********************/
    public class MyBinder extends Binder{
        LocationBackgroundService getService(){
            return LocationBackgroundService.this;
        }
    }
    /************************* Custom Functions *********************/
    //Checks Whether we are requesting for updates
    public boolean isRequestingLocationUpdates(){
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("IsRequestingLocationUpdates",false);
    }

    //set status on whether we are requesting updates or not
    public void setRequestingLocationUpdates(Boolean status){
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("IsRequestingLocationUpdates",status).apply();
    }

    //To build the notification for foreground service
    private Notification getNotification(Double...coordinates) {
        Intent intent = new Intent(this, LocationBackgroundService.class);
        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(is_started_from_notification, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, HomeActivity.class), 0);
        String notification_content;
        if(coordinates.length!=0){
            notification_content="Your latitude is "+Double.toString(coordinates[0])+"And Longitude is "+Double.toString(coordinates[1]);
        }else {
            Log.i(TAG,"Notification content does not have location right now");
            notification_content="Your location is being shared";
        }

        return new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_location_on_black_24dp,"Open",
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel_black_24dp, "Stop Location Updates",
                        servicePendingIntent)
                .setContentText(notification_content) //TODO need to set some dynamic text giving more info of what is going around in the service
                .setContentTitle("WhereAbouts")
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setSmallIcon(R.drawable.position)
                .setTicker("Your location is being shared")
                .setWhen(System.currentTimeMillis()).build();
    }
    //Initialising the location callback
    private void createLocationCallback() {
        location_callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
                /*DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference("users/123");
                String userId = Integer.toString(iterative_key);
                LocationData LocationData = new LocationData(current_location.getLatitude(),current_location.getLongitude() );
                mDatabase.child(userId).setValue(LocationData);*/
            }
        };
    }

    //Checking whether the service is currently running as a foreground service this would help update the notification
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }

    //Create a Location request
    private void createLocationRequest() {
        location_request = new LocationRequest();
        location_request.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        location_request.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        location_request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }
    public void startTracking(){
        try {
            startService(new Intent(getApplicationContext(), LocationBackgroundService.class));
        } catch (Exception e) {
            Log.i(TAG, "Start Service Exception " + e.toString());
        }
        setRequestingLocationUpdates(true);
    }

    //Handle a new Location Update
    private void onNewLocation(Location location){
        if(previous_location==null){
            previous_location=location;
        }else {
            if(previous_location.distanceTo(location)>10){
                //Here we have a significant change in the distance of the position thus we need to update the firebase
                ++iterative_key;
                Log.d(TAG, "The Latitude is : " + Double.toString(location.getLatitude()) + "and the longitude is : " + Double.toString(location.getLongitude())+"and the iterative counter is "+Integer.toString(iterative_key));
                //Send Data to Firebase

                // Update notification content if running as a foreground service.
                if (serviceIsRunningInForeground(this)) {
                    notification_manager.notify(NOTIFICATION_ID, getNotification(location.getLatitude(),location.getLongitude()));
                }
            }
            else {
                previous_location=location;
            }
        }
        //Broadcast Result To Activity only if we are not in foreground
        /*Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);*/

    }

    //Setup Location Updates
    public void requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates");
        try {
            mFusedLocationClient.requestLocationUpdates(location_request,
                    location_callback,handler_thread_looper );
        } catch (SecurityException unlikely) {
            setRequestingLocationUpdates(false);
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    //remove location update service
    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        try {
            mFusedLocationClient.removeLocationUpdates(location_callback);
            setRequestingLocationUpdates(false);
            stopSelf();
        } catch (SecurityException unlikely) {
            setRequestingLocationUpdates(true);
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }

}
