/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.Disposable
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Registers a coroutine for notifications on Android Studio Events being logged.
 * This code is in a separate file because StatisticsViewer is written in java
 * which does not support coroutines.
 */
object StatisticsViewerListener {
  @JvmStatic
  fun register(disposable: Disposable, callback: (AndroidStudioEvent.Builder) -> Unit) {
    AndroidCoroutineScope((disposable)).launch {
      AndroidStudioUsageTracker.channel.openSubscription().consumeEach {
        callback(it)
      }
    }
  }
}