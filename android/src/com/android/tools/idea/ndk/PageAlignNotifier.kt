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
package com.android.tools.idea.ndk

import com.android.SdkConstants
import com.android.ide.common.pagealign.AlignmentProblems.ElfLoadSectionsNot16kAligned
import com.android.ide.common.pagealign.AlignmentProblems.ElfNot16kAlignedInZip
import com.android.ide.common.pagealign.findElfFile16kAlignmentInfo
import com.android.tools.idea.ndk.PageAlignConfig.createSoNotAlignedInZipMessage
import com.android.tools.idea.ndk.PageAlignConfig.createSoUnalignedLoadSegmentsMessage
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.Align16kbEvent
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_BUBBLE_LOAD_SECTIONS_DEPLOYED
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_BUBBLE_ZIP_OFFSET_DEPLOYED
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import javax.swing.event.HyperlinkEvent

/**
 * Encapsulates the logic of when to show a warning bubble and when to log events for the 16 KB page alignment check.
 */
abstract class PageAlignNotifier(
  val balloonsEnabled : Boolean = PageAlignConfig.isPageAlignMessageEnabled()) {
  abstract fun showBalloon(text : String)
  abstract fun logUsage(event : AndroidStudioEvent.Builder)

  fun notify16kbAlignmentViolations(apkInfo: ApkInfo, productCpuAbiList: List<String>, buildCharacteristics: String?) {
    fun logEvent(type : AlignNative16kbEventType) {
      val event = Align16kbEvent.newBuilder()
        .setType(type)
      event.productCpuAbilist = productCpuAbiList.joinToString(",")
      if (buildCharacteristics != null) {
        event.buildCharacteristics = buildCharacteristics
      }
      logUsage(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.ALIGN16KB_EVENT)
                         .setProjectId(AnonymizerUtil.anonymizeUtf8(apkInfo.applicationId))
                         .setRawProjectId(apkInfo.applicationId)
                         .setAlign16KbEvent(event))
    }
    // Determine whether this is an ARM64 device or emulator.
    val deviceIsArm64 = productCpuAbiList.contains(SdkConstants.ABI_ARM64_V8A)
    // Determine whether this is a device type we should show a bubble for.
    val buildCharacteristics = buildCharacteristics?.split(",") ?: emptyList()
    val isWear = buildCharacteristics.contains("watch")
    val isAutomotive = buildCharacteristics.contains("automotive")

    for (apk in apkInfo.files) {
      val apkFile = apk.apkFile
      if (!apkFile.exists()) continue
      // Find any alignment issues in the APK
      val alignmentInfo = findElfFile16kAlignmentInfo(apkFile)
      // Don't log metrics or show bubble if there are no ELF files in the APK.
      if (!alignmentInfo.hasElfFiles) continue
      // Report whether the app is 16 KB compliant
      val problems = alignmentInfo.alignmentProblems
      if (problems.isEmpty()) {
        logEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
        continue
      }
      logEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
      // Show a bubble only if the server flag is enabled
      if (!balloonsEnabled) continue
      // Only show a bubble for ARM64
      if (!deviceIsArm64) continue
      // Don't show bubble for devices where it doesn't apply
      // TODO(382091362) confirm this is a complete list of devices we shouldn't show a bubble for.
      if (isWear || isAutomotive) continue

      // Show a warning balloon for the SO files that aren't aligned at a 16 KB boundary within the APK
      problems.filter { it.value.contains(ElfNot16kAlignedInZip) }.map { it.key }.let { files ->
        if (files.isNotEmpty()) {
          logEvent(ALIGN_NATIVE_BUBBLE_ZIP_OFFSET_DEPLOYED)
          val message = createSoNotAlignedInZipMessage(apkFile, files)
          showBalloon(message)
        }
      }

      // Show a warning balloon for SO files that have LOAD sections not aligned on 16 KB boundary
      problems.filter { it.value.contains(ElfLoadSectionsNot16kAligned) }.map { it.key }.let { files ->
        if (files.isNotEmpty()) {
          logEvent(ALIGN_NATIVE_BUBBLE_LOAD_SECTIONS_DEPLOYED)
          val message = createSoUnalignedLoadSegmentsMessage(apkFile, files)
          showBalloon(message)
        }
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
}