package com.example.targetsdkupgradesample32_33.wifi;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.targetsdkupgradesample32_33.R;

public class WifiActivity extends AppCompatActivity {
    private WifiHotspotManager hotspotManager;
    private static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hotspotManager = new WifiHotspotManager(this);
        Log.d("Test-log","WifiActibvity");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void turnOnHotspot(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    MY_PERMISSIONS_ACCESS_FINE_LOCATION);


        }
        else {
            hotspotManager.turnOnHotspot();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    hotspotManager.turnOnHotspot();
                }
            }
        }
    }
}