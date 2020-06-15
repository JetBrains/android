/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.instantappsample.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity that manages a service which can either be started or bound.
 */
public class ServiceManagementActivity extends AppCompatActivity {

    private static final String TAG = "ServiceManagement";

    private boolean mServiceStarted;
    private boolean mServiceBound;
    private View.OnClickListener mOnClickListener;
    private Button mStartedServiceButton;
    private Button mBoundServiceButton;
    private TimekeepingService.TimeBinder mBinder;
    private TextView mInfoView;

    /**
     * Connection to the bound service. Allows communication with said service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (TimekeepingService.TimeBinder) service;
            mServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);
        bindViews();
    }

    @Override
    protected void onStop() {
        if (mBinder != null && mBinder.isBinderAlive()) toggleBoundService();
        if (mServiceStarted) toggleStartedService();
        super.onStop();

    }

    private void bindViews() {
        mStartedServiceButton = (Button) findViewById(R.id.btn_service_start);
        mStartedServiceButton.setOnClickListener(getOnClickListener());

        mBoundServiceButton = (Button) findViewById(R.id.btn_service_bind);
        mBoundServiceButton.setOnClickListener(getOnClickListener());

        mInfoView = (TextView) findViewById(R.id.infoView);
    }

    @NonNull
    private View.OnClickListener getOnClickListener() {
        if (mOnClickListener == null) {
            mOnClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final int viewId = v.getId();
                    if (viewId == R.id.btn_service_start) {
                        toggleStartedService();
                    } else if (viewId == R.id.btn_service_bind) {
                        toggleBoundService();
                    } else {
                        Log.d(TAG, "Not implemented for view: " +
                                getResources().getResourceEntryName(v.getId()));

                    }
                }
            };
        }
        return mOnClickListener;
    }

    /**
     * Fires start and stop intents towards the service.
     * Be aware that the service is not being stopped while it's bound.
     */
    private void toggleStartedService() {
        if (mServiceStarted) {
            stopService(TimekeepingService.getIntent(this));
            mStartedServiceButton.setText(R.string.btn_service_start);
        } else {
            startService(TimekeepingService.getIntent(this));
            mStartedServiceButton.setText(R.string.btn_service_stop);
        }
        mServiceStarted = !mServiceStarted;
        Log.d(TAG, "Service started == " + mServiceStarted);
    }

    /**
     * Binds and unbinds the service.
     */
    private void toggleBoundService() {
        if (mServiceBound && mBinder != null) {
            String text = getString(R.string.service_ran_for, mBinder.getTimeRunningFormatted());
            mInfoView.setText(text);
            unbindService(mConnection);
            mBoundServiceButton.setText(R.string.btn_service_bind);
        } else {
            bindService(TimekeepingService.getIntent(this),
                    mConnection,
                    Context.BIND_AUTO_CREATE);
            mBoundServiceButton.setText(R.string.btn_service_unbind);
        }
        mServiceBound = !mServiceBound;
        Log.d(TAG, "Service bound == " + mServiceBound);

    }
}
