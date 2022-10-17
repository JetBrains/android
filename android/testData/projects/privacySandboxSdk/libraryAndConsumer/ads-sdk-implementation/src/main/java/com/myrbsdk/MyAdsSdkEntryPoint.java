package com.myrbsdk;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


import androidx.annotation.RequiresApi;

import java.util.concurrent.Executor;

@RequiresApi(api = 34)
public class MyAdsSdkEntryPoint extends SandboxedSdkProvider {

    @Override
    public void initSdk(SandboxedSdkContext sandboxedSdkContext, Bundle bundle,
                        Executor executor, InitSdkCallback initSdkCallback) {
        Log.i("SDK", "initSdk");
        executor.execute(() -> initSdkCallback.onInitSdkFinished(new Bundle()));
    }

    @Override
    public View getView(Context windowContext, Bundle bundle) {
        Log.i("SDK", "getView");
        TextView textView = new TextView(windowContext);
        textView.setText("Hello from the SDK!");
        return textView;
    }

    @Override
    public void onDataReceived(Bundle bundle, DataReceivedCallback dataReceivedCallback) {
        Log.i("SDK", "onDataReceived");
    }
}
