/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.adtui.webp.WebpMetadata.Companion.ensureWebpRegistered
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.UsageTracker.ideBrand
import com.android.tools.analytics.UsageTracker.ideaIsInternal
import com.android.tools.analytics.UsageTracker.version
import com.android.tools.idea.analytics.SystemInfoStatsMonitor
import com.android.tools.idea.analytics.currentIdeBrand
import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.android.tools.idea.stats.AndroidStudioUsageTracker.setup
import com.android.tools.idea.stats.ConsentDialog.Companion.showConsentDialogIfNeeded
import com.intellij.concurrency.JobScheduler
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

//import com.intellij.analytics.AndroidStudioAnalytics;
/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 *
 *
 * **Note:** Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * [GradleSpecificInitializer] instead.
 *
 */
class AndroidStudioInitializer : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    configureUpdateUrls()
    setupAnalytics()

    // Initialize System Health Monitor after Analytics.
    asyncScope.launch {
      AndroidStudioSystemHealthMonitor.getInstance()?.start()
    }

    // TODO: Remove this once the issue has been properly fixed in the IntelliJ platform
    //  see https://youtrack.jetbrains.com/issue/IDEA-316037
    // Automatic registration of WebP support through the WebP plugin can fail
    // because of a race condition in the creation of IIORegistry.
    // Trying again here ensures that the WebP support is correctly registered.
    WebpMetadata.ensureWebpRegistered()

    if (IdeSdks.getInstance().androidSdkPath != null) {
      val handler =
        AndroidSdkHandler.getInstance(AndroidLocationsSingleton, IdeSdks.getInstance().androidSdkPath!!.toPath())
      // We need to start the system info monitoring even in case when user never
      // runs a single emulator instance: e.g., incompatible hypervisor might be
      // the reason why emulator is never run, and that's exactly the data
      // SystemInfoStatsMonitor collects
      SystemInfoStatsMonitor().start()
    }
  }

  /** Sets up collection of Android Studio specific analytics.  */
  private fun setupAnalytics() {
    //AndroidStudioAnalytics.getInstance().initializeAndroidStudioUsageTrackerAndPublisher()

    UsageTracker.version = ApplicationInfo.getInstance().strictVersion
    UsageTracker.ideBrand = currentIdeBrand()
    if (ApplicationManager.getApplication().isInternal) {
      UsageTracker.ideaIsInternal = true
    }
    AndroidStudioUsageTracker.setup(JobScheduler.getScheduler())
  }

  /** Configures update URLs based on environment variables. This makes it easier to do local testing.  */
  private fun configureUpdateUrls() {
    // If defined, AS_UPDATE_URL should point to the *root* of the updates.xml file to use
    // and patches are expected to be in the same folder.
    var updateUrl = System.getenv("AS_UPDATE_URL")
    if (updateUrl != null) {
      if (!updateUrl.endsWith("/")) {
        updateUrl += "/"
      }
      // Set the Java system properties expected by UpdateChecker.
      System.setProperty("idea.updates.url", updateUrl + "updates.xml")
      System.setProperty("idea.patches.url", updateUrl)
    }
  }
}
