// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.startup

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.ide.ApplicationInitializedListener

/**
 * Initializer that is run only when the Android plugin is executed in IntelliJ IDEA.
 * This initializer shouldn't be run in Android Studio.
 */
private class AndroidPluginIdeaInitializer : ApplicationInitializedListener {
  override suspend fun execute() {
    AnalyticsSettings.disable();
    UsageTracker.disable();
    UsageTracker.ideBrand = AndroidStudioEvent.IdeBrand.INTELLIJ
  }
}