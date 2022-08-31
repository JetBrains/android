package com.example.rubidumconsumer;

import android.annotation.SuppressLint;
import android.app.sdksandbox.SdkSandboxManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;

@SuppressLint("NewApi")
public class MainActivity extends AppCompatActivity {

    // Needs to be the package name of the SDK as defined in the SdkModulesConfig.pb.json when building the SDK
    static final String SDK_PACKAGE_NAME = "com.myrbsdk";

    private SdkSandboxManager sdkSandboxManager;

    private RemoteSdkCallbackImpl callback;
    private SurfaceView renderedView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        renderedView = findViewById(R.id.rendered_view);
        sdkSandboxManager = getApplicationContext().getSystemService(
                SdkSandboxManager.class);
    }

    @Override
    public void onResume() {
        super.onResume();
        renderedView.setVisibility(View.INVISIBLE);
        callback = new RemoteSdkCallbackImpl(this);
        sdkSandboxManager.loadSdk(SDK_PACKAGE_NAME, new Bundle(), Runnable::run, callback);
    }

    void requestSurfacePackage() {
        final WindowMetrics metrics = getApplicationContext().getSystemService(
                WindowManager.class).getCurrentWindowMetrics();

        sdkSandboxManager.requestSurfacePackage(
                SDK_PACKAGE_NAME, getDisplay().getDisplayId(),
                metrics.getBounds().width(),
                metrics.getBounds().height(),
                new Bundle(),
                Runnable::run,
                callback);
    }

    void renderSurfaceView(SurfaceControlViewHost.SurfacePackage surfacePackage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            renderedView.setChildSurfacePackage(surfacePackage);
            renderedView.setVisibility(View.VISIBLE);
        });
    }
}
