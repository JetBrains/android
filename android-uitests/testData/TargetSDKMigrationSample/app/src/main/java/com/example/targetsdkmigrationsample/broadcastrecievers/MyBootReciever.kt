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
package com.example.targetsdkmigrationsample.broadcastrecievers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.targetsdkmigrationsample.services.MyDataSyncService

/**
 * This class test the targetSDK filter for behavior changes in
 * API 35 for Restrictions on BOOT_COMPLETED broadcast receiver.
 *
 * More details are present in below link
 * https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
 */
class MyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("MyBootReceiver", "Boot completed!")
            Toast.makeText(context, "Device booted!", Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(context, MyDataSyncService::class.java)
            context.startService(serviceIntent)
        }
    }
}