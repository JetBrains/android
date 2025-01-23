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

import com.android.ide.common.pagealign.findElfFile16kAlignmentProblems
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.ndk.PageAlignConfig.createSoNotAlignedInZipMessage
import com.android.tools.idea.ndk.PageAlignConfig.createSoUnalignedLoadSegmentsMessage
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch
import com.android.ide.common.pagealign.AlignmentProblems.ElfLoadSectionsNot16kAligned
import com.android.ide.common.pagealign.AlignmentProblems.ElfNot16kAlignedInZip
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.configuration.execution.ApplicationDeployListener
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import javax.swing.event.HyperlinkEvent

/**
 * Listen for app deploy events and check whether there are 16 KB alignment issues.
 * If there are, then alert the user.
 */
class PageAlignDeployListener(private val project : Project) : ApplicationDeployListener {
  override fun beforeDeploy(apkInfo: ApkInfo) {
    project.coroutineScope.launch {
      withBackgroundProgress(project, "Checking 16 KB alignment") {
        notify16kbAlignmentViolations(apkInfo)
      }
    }
  }

  /**
   * Listen for hyperlink activations and open a browser with the url from href.
   */
  val hyperlinkListener : NotificationListener = object : NotificationListener.Adapter() {
    override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        e.url?.let {
          BrowserUtil.browse(it)
        }
      }
    }
  }

  private fun notify16kbAlignmentViolations(apkInfo : ApkInfo) {
    // Nothing to do if the build didn't create an APK
    for (apk in apkInfo.files) {
      val apkFile = apk.apkFile
      if (!apkFile.exists()) return
      // Find any alignment issues in the APK
      val problems = findElfFile16kAlignmentProblems(apkFile)
      if (problems.isEmpty()) return

      // Show a warning balloon for the SO files that aren't aligned at a 16 KB boundary within the APK
      problems.filter { it.value.contains(ElfNot16kAlignedInZip) }.map { it.key }.let { files ->
        if (files.isNotEmpty()) {
          val message = createSoNotAlignedInZipMessage(apkFile, files)
          AndroidNotification.getInstance(project)
            .showBalloon("Android 16 KB Alignment", message, WARNING, hyperlinkListener)
        }
      }

      // Show a warning balloon for SO files that have LOAD sections not aligned on 16 KB boundary
      problems.filter { it.value.contains(ElfLoadSectionsNot16kAligned) }.map { it.key }.let { files ->
        if (files.isNotEmpty()) {
          val message = createSoUnalignedLoadSegmentsMessage(apkFile, files)
          AndroidNotification.getInstance(project)
            .showBalloon("Android 16 KB Alignment", message, WARNING, hyperlinkListener)
        }
      }
    }
  }
}
