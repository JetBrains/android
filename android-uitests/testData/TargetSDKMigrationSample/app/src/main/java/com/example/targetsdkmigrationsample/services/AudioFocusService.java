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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.targetsdkmigrationsample.R;

/**
 * This class test the targetSDK filter for behavior changes in
 * API 35 related to calling Audio Focus from background service.
 * More details are present in below link
 * <a href="https://developer.android.com/about/versions/15/behavior-changes-15#audio-focus">...</a>
 */
public class AudioFocusService extends android.app.Service {
    private static final String TAG = "AudioFocusService";
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    // Resume or start audio recording
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    // Stop or pause audio recording and release resources
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Pause audio recording
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Lower the volume of audio recording
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        startForeground(1, createNotification());
        requestAudioFocus();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Keep service running until explicitly stopped
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        abandonAudioFocus();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void requestAudioFocus() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        if (Build.VERSION.SDK_INT >= 26) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Failed to acquire audio focus");
            }
        } else {
            // Fallback for older Android versions
            int result = audioManager.requestAudioFocus(
                    new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {
                            // Handle focus change
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Failed to acquire audio focus");
            }
        }
    }

    private void abandonAudioFocus() {
        if (audioFocusRequest != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("audio_focus_channel", "Audio Focus Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "audio_focus_channel")
                .setSmallIcon(R.drawable.ic_launcher_background) // Replace with your app icon
                .setContentTitle("Audio Focus Service")
                .setContentText("Playing audio in the background");
        return builder.build();
    }
}