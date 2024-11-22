/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.example.targetsdkmigrationsample.services;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * This class test the targetSDK filter for behavior changes in
 * API 35 related to DND settings changes..
 * More details are present in below link
 * <a href="https://developer.android.com/about/versions/15/behavior-changes-15#dnd-changes">...</a>
 */
public class DNDUpdateService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Set interruption filter to allow only priority interruptions
        int interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        notificationManager.setInterruptionFilter(interruptionFilter);

        // Define a notification policy to allow calls and messages from contacts
        NotificationManager.Policy notificationPolicy = new NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS |
                        NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES,
                NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
                NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS
        );
        notificationManager.setNotificationPolicy(notificationPolicy);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
