package com.example.nipunarora.spotme.Services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.RingtoneManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.example.nipunarora.spotme.Activities.HomeActivity;
import com.example.nipunarora.spotme.DataModels.LocationData;
import com.example.nipunarora.spotme.Interfaces.ServiceToActivityMail;
import com.example.nipunarora.spotme.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

/**
 * Created by nipunarora on 19/07/17.
 */

public class LocationBackgroundGetUpdatesService extends Service {
    final String TAG="GetUpdatesService";
    public String is_started_from_notification="isStartedFromNotification";
    private final IBinder mBinder=new MyBinder();
    NotificationManager notification_manager;
    Boolean mChangingConfiguration;
    private static final int NOTIFICATION_ID = 1234;
    String person_being_tracked;
    WeakReference<ServiceToActivityMail> mailer;
    DatabaseReference firebase_ref;
    ValueEventListener db_listener;
    /************************** Overrides ********************/
    @Override
    public void onCreate() {
        super.onCreate();
        notification_manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        boolean startedFromNotification = intent.getBooleanExtra(is_started_from_notification,
                false);

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            stopTracking();
            stopSelf();
        }else{

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
        if(!mChangingConfiguration && isTracking()){
            Log.i(TAG, "Starting foreground service");
            startForeground(NOTIFICATION_ID, getNotification("null"));
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration=true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    /********************** Custom Binder Class **************/
    public class MyBinder extends Binder {
        public LocationBackgroundGetUpdatesService getService(){
            return LocationBackgroundGetUpdatesService.this;
        }
    }

    /***************************** Custom Functions *********************/
    //Checks Whether we are requesting for updates
    public boolean isTracking(){
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("IsTracking",false);
    }
    //To set Tracking status
    public void setTrackingStatus(Boolean status){
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("IsTracking",status).apply();
    }

    //startTracking
    public void startTracking(String person_name){
        startService(new Intent(getApplicationContext(),LocationBackgroundGetUpdatesService.class));
        setTrackingStatus(true);
        person_being_tracked=person_name;
        registerFirebaseClient();
        Log.i(TAG,"startTracking");

    }

    //To build the notification for foreground service
    private Notification getNotification(String location) {
        Intent intent = new Intent(this, LocationBackgroundGetUpdatesService.class);
        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(is_started_from_notification, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, HomeActivity.class), 0);
        String notification_content;
        if(location!="null"){
            notification_content=person_being_tracked+" is at "+location;
        }else {
            Log.i(TAG,"Notification content does not have location right now");
            notification_content="Waiting for " + person_being_tracked;
        }

        return new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_location_on_black_24dp,"Open",
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel_black_24dp, "Stop Tracking",
                        servicePendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(notification_content))
                //.setContentText(notification_content) //TODO need to set some dynamic text giving more info of what is going around in the service
                .setContentTitle("WhereAbouts")
                .setContentText("Expand To View")
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setSmallIcon(R.drawable.position)
                .setTicker("Waiting for "+person_being_tracked)
                .setWhen(System.currentTimeMillis()).build();
    }
    //Method to stop tracking the person that is stop listening for the Database updates
    public void stopTracking(){
        setTrackingStatus(false);
        try{
            //Remove firebase Listener
            firebase_ref.removeEventListener(db_listener);
            setTrackingStatus(false);
            stopSelf();
        }catch (Exception e){
            Log.i(TAG,"stopTracking:"+e.toString());
        }
    }

    //Registering Firebase Client and database connection
    public void registerFirebaseClient(){
        //Connect with firebase and register client
        //Currently setting up some sample details
        firebase_ref= FirebaseDatabase.getInstance().getReference("Sample").child("1");
        db_listener= new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    LocationData temp = (LocationData) dataSnapshot.getValue(LocationData.class);
                    Log.d(TAG,"onDataChange:"+temp.address);
                    if (getUpdateServiceIsRunningInForeground(LocationBackgroundGetUpdatesService.this)) {
                        notification_manager.notify(NOTIFICATION_ID, getNotification(temp.address));
                    }
                    ServiceToActivityMail m=mailer.get();
                    if(m!=null) {
                        m.onReceiveServiceMail("LocationUpdate", temp.address);
                    }
                }catch (Exception e){
                    Log.i(TAG,"Firebase:onDataChange: "+e.toString());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
        firebase_ref.addValueEventListener(db_listener);

    }

    //Register Activity Client
    public void registerActivityClient(ServiceToActivityMail mail_client){
        mailer=new WeakReference<ServiceToActivityMail>(mail_client);
    }
    //Checking whether the get update service is currently running as a foreground service this would help update the notification
    public boolean getUpdateServiceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground ) {
                    return true;
                }
            }
        }
        return false;
    }

}
