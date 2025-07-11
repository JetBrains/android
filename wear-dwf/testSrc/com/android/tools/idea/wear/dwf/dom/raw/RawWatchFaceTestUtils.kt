/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

fun manifestWithWFFVersion(version: String) =
  // language=XML
  """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.android.tools.idea.wear.dwf.dom.raw">
    <uses-feature android:name="android.hardware.type.watch" />
    <application
        android:icon="@drawable/preview"
        android:label="@string/app_name"
        android:hasCode="false"
        >

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <property
            android:name="com.google.wear.watchface.format.version"
            android:value="$version" />
        <property
            android:name="com.google.wear.watchface.format.publisher"
            android:value="Test publisher" />

        <uses-library
            android:name="com.google.android.wearable"
            android:required="false" />
    </application>
</manifest>
        """
    .trimIndent()