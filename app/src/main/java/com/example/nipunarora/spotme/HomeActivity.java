package com.example.nipunarora.spotme;

import android.*;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.icu.text.DateFormat;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import static android.R.attr.permission;

/**
 * Created by nipunarora on 21/06/17.
 */

public class HomeActivity extends AppCompatActivity {
    String permission = Manifest.permission.ACCESS_FINE_LOCATION;
    Integer requestCode=77;
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient settings_client;
    LocationRequest location_request;
    LocationSettingsRequest location_setting_request;
    Button start_updates,stop_updates;
    Boolean are_we_requesting_location_updates=false;
    Location current_location;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    final String TAG="HomeActivityTag";
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    LocationBackgroundService location_background_service;
    private  ServiceConnection mServiceConnection;
    Boolean mBound;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        start_updates=(Button)findViewById(R.id.startUpdates);
        stop_updates=(Button)findViewById(R.id.stopUpdates);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settings_client=LocationServices.getSettingsClient(this);


        //Call the functions to setup location updates
        createLocationRequest();
        buildLocationSettingsRequest();
        //set On click listener
        start_updates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermissions()) {
                    startLocationUpdates();
                } else if (!checkPermissions()) {
                    askForPermission(requestCode,permission);
                }

            }
        });
        stop_updates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                location_background_service.removeLocationUpdates();
            }
        });
        //Setup A service connection to bind to a running service
        mServiceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                LocationBackgroundService.MyBinder binder = (LocationBackgroundService.MyBinder) service;
                location_background_service= binder.getService();
                mBound = true;
                Log.i(TAG,"Connected to location background service");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                location_background_service = null;
                mBound = false;
            }
        };

    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, LocationBackgroundService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG,"Received Permission");
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mBound){
            unbindService(mServiceConnection);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        are_we_requesting_location_updates = false;
                        break;
                }
                break;
        }
    }
    /********************************* Custom Functions ****************************/

    private void createLocationRequest() {
        location_request = new LocationRequest();
        location_request.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        location_request.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        location_request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(location_request);
        location_setting_request = builder.build();
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        settings_client.checkLocationSettings(location_setting_request)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");
                        are_we_requesting_location_updates=true;
                        //noinspection MissingPermission
                        location_background_service.startTracking();

                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e; //the exception here results from google services api
                                    rae.startResolutionForResult(HomeActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(HomeActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                are_we_requesting_location_updates = false;
                        }
                    }
                });
    }
    //A method invoked to ask user for permissions
    private void askForPermission(final Integer requestCode,final String... permission) {
        Boolean did_user_deny_location= ActivityCompat.shouldShowRequestPermissionRationale(HomeActivity.this, permission[0]);
        if (ContextCompat.checkSelfPermission(HomeActivity.this, permission[0]) == PackageManager.PERMISSION_GRANTED ) {
            Toast.makeText(this, "" + permission + " is already granted.", Toast.LENGTH_SHORT).show();
            startLocationUpdates();
        } else {
            //The if condition below would work if the user has denied one particular permission or both
            if (did_user_deny_location) {
                //When user denied access to both the camera and external storage
                setupPermissionDialog(requestCode, "We need to access your location to allow you to share your location with others", permission);
            }
            else {
                setupPermissionDialog(requestCode,"Allow access to location", permission);
            }
        }
    }

    private void setupPermissionDialog(final Integer requestCode,String message,final String... permission)
    {
        showMessageOKCancel(message,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(HomeActivity.this, permission, requestCode);
                    }
                });
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(HomeActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }



}


