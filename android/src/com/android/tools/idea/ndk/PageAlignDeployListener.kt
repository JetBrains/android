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
package com.android.tools.idea.ndk

import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.configuration.execution.ApplicationDeployListener
import com.google.wireless.android.sdk.stats.AndroidStudioEvent

/**
 * Listen for app deploy events and check whether there are 16 KB alignment issues.
 * If there are, then alert the user.
 */
class PageAlignDeployListener(private val project : Project) : ApplicationDeployListener {
  override fun beforeDeploy(device : IDevice, apkInfo: ApkInfo) {
    project.coroutineScope.launch {
      withBackgroundProgress(project, "Checking 16 KB alignment") {
        Notifier(AndroidNotification.getInstance(project)).notify16kbAlignmentViolations(
          apkInfo,
          productCpuAbiList = device.abis,
          buildCharacteristics = device.getProperty("ro.build.characteristics")
        )
      }
    }
  }

  private class Notifier(val notification : AndroidNotification) : PageAlignNotifier() {
    override fun showBalloon(text: String) = notification.showBalloon("Android 16 KB Alignment", text, WARNING, hyperlinkListener)
    override fun logUsage(event: AndroidStudioEvent.Builder) =  UsageTracker.log(event)
  }
}
