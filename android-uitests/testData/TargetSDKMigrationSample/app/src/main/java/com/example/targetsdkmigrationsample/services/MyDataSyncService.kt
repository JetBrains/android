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
package com.example.targetsdkmigrationsample.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.targetsdkmigrationsample.MainActivity
import com.example.targetsdkmigrationsample.R

/**
 * This class test the targetSDK filter for behavior changes in
 * API 35 for foreground services. All the Data sync foreground services
 * should override Service.timeout(int, int) and call stopSelf() method in API 35.
 * More details are present in below link
 * https://developer.android.com/about/versions/15/behavior-changes-15#datasync-timeout
 */
class MyDataSyncService : Service() {

    private val notificationId = 1 // Unique ID for your notification

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        // Start the foreground service
        startForeground(notificationId, createNotification())
        return START_NOT_STICKY // Or other return flags as needed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    // Create the notification for the foreground service
    private fun createNotification(): Notification {
        val channelId = "data_sync_channel" // Create a notification channel
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Data Sync")
            .setContentText("Syncing data...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your icon
            .setContentIntent(pendingIntent)
            .build()
        return notification
    }

    // For app targets API 26 and above, create a notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Data Sync Channel"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("channelId", channelName, importance)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

}