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
package com.android.tools.idea.startup

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.ide.ApplicationInitializedListener

/**
 * Initializer that is run only when the Android plugin is running inside IntelliJ IDEA.
 * This code does *not* run in Android Studio.
 */
@Suppress("UnstableApiUsage")
class AndroidPluginIdeaInitializer: ApplicationInitializedListener {
  override suspend fun execute() {
    AnalyticsSettings.disable()
    UsageTracker.disable()
    UsageTracker.ideBrand = AndroidStudioEvent.IdeBrand.INTELLIJ
  }
}
