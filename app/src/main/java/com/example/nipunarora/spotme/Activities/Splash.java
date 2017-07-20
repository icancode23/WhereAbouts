package com.example.nipunarora.spotme.Activities;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.example.nipunarora.spotme.Activities.HomeActivity;
import com.example.nipunarora.spotme.R;

public class Splash extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent i=new Intent(getApplicationContext(),HomeActivity.class);
                startActivity(i);
                finish();
            }
        }, 3000);

    }
}
