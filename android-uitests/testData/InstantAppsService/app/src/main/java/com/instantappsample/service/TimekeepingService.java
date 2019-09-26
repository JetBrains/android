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

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A simple {@link Service} that keeps track of it's own running time.
 */

public class TimekeepingService extends Service {

    class TimeBinder extends Binder {
        @SuppressLint("SimpleDateFormat")
        private SimpleDateFormat mFormatter = new SimpleDateFormat("mm:ss.SSS");

        long getTimeRunning() {
            return System.currentTimeMillis() - mStartTimeMillis;
        }

        String getTimeRunningFormatted() {
            return mFormatter.format(new Date(getTimeRunning()));
        }
    }

    private long mStartTimeMillis = 0;

    private TimeBinder mBinder = new TimeBinder();

    @NonNull
    public static Intent getIntent(Context context) {
        return new Intent(context, TimekeepingService.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        doSomething();
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        doSomething();
        return mBinder;
    }

    /**
     * Kick off the service's work.
     */
    private void doSomething() {
        if (mStartTimeMillis == 0) {
            mStartTimeMillis = System.currentTimeMillis();
        }
        // This is where a service should do it's actual work.
    }
}
