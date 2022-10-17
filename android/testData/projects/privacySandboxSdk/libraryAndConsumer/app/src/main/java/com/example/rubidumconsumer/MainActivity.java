// TODO(b/240533883): Re-enable commented out parts when the T preview SDK is imported
package com.example.rubidumconsumer;

import android.annotation.SuppressLint;
// import android.app.sdksandbox.SdkSandboxManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControlViewHost;
import android.view.View;

// import com.example.rubidumconsumer.databinding.ActivityMainBinding;

@SuppressLint("NewApi")
public class MainActivity extends AppCompatActivity {

    static final String SDK_PACKAGE_NAME = "com.myrbsdk"; // Needs to be the package name of the SDK as defined in the SdkModulesConfig.pb.json when building the SDK

    //private SdkSandboxManager sdkSandboxManager;

    //private RemoteSdkCallbackImpl callback;
    //private ActivityMainBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //binding = ActivityMainBinding.inflate(getLayoutInflater());
        //View view = binding.getRoot();
        //setContentView(view);
        //sdkSandboxManager = getApplicationContext().getSystemService(
        //        SdkSandboxManager.class);
    }

    @Override
    public void onResume() {
        super.onResume();
        //binding.renderedView.setZOrderOnTop(true);
        //binding.renderedView.setVisibility(View.INVISIBLE);

        //callback = new RemoteSdkCallbackImpl(this);
        //sdkSandboxManager.loadSdk(SDK_PACKAGE_NAME, new Bundle(), Runnable::run, callback);
    }

    //void requestSurfacePackage() {
    //    sdkSandboxManager.requestSurfacePackage(
    //            SDK_PACKAGE_NAME, getDisplay().getDisplayId(),
    //            200, 200, new Bundle(), Runnable::run, callback);
    //}
    //
    //void renderSurfaceView(SurfaceControlViewHost.SurfacePackage surfacePackage) {
    //    new Handler(Looper.getMainLooper()).post(() -> {
    //        binding.renderedView.setChildSurfacePackage(surfacePackage);
    //        binding.renderedView.setVisibility(View.VISIBLE);
    //    });
    //}
}
