// TODO(b/240533883): Re-enable commented out parts when the T preview SDK is imported
//package com.example.rubidumconsumer;
//
//import android.annotation.SuppressLint;
//import android.app.sdksandbox.SdkSandboxManager.LoadSdkCallback;
//import android.app.sdksandbox.SdkSandboxManager.RequestSurfacePackageCallback;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.SurfaceControlViewHost;
//import androidx.annotation.NonNull;
//
//@SuppressLint("NewApi")
//public class RemoteSdkCallbackImpl implements LoadSdkCallback, RequestSurfacePackageCallback {
//
//    RemoteSdkCallbackImpl(MainActivity mainActivity) {
//        this.mainActivity = mainActivity;
//    }
//
//    private MainActivity mainActivity;
//
//    @Override
//    public void onLoadSdkSuccess(Bundle bundle) {
//        Log.i("App", "onLoadSdkSuccess");
//        mainActivity.requestSurfacePackage();
//    }
//
//    @Override
//    public void onLoadSdkFailure(int errorCode, String errorMessage) {
//        Log.i("App", String.format("onLoadSdkFailure. Error code: %d, Error message: %s",errorCode, errorMessage));
//    }
//
//    @Override
//    public void onSurfacePackageReady(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage,
//                                      int i, @NonNull Bundle bundle) {
//        Log.i("App", "onSurfacePackageReady");
//        mainActivity.renderSurfaceView(surfacePackage);
//    }
//
//    @Override
//    public void onSurfacePackageError(int errorCode, String errorMessage) {
//        Log.i("App", "onSurfacePackageError");
//    }
//}

