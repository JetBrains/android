/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea

import com.intellij.idea.customization.base.IntelliJIdeaExternalResourceUrls
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.annotations.VisibleForTesting

class AndroidStudioResourceUrls : ExternalProductResourceUrls {
  companion object {
    // The overriding AS_UPDATE_URL environment variable is used by QA and some E2E tests (UpdateTest).
    private val UPDATE_BASE_URL: String = System.getenv("AS_UPDATE_URL") ?: "https://dl.google.com/android/studio/patches"
  }

  private val jetbrainsUrls = IntelliJIdeaExternalResourceUrls()

  override val updateMetadataUrl: Url
    get() = Urls.newFromEncoded(UPDATE_BASE_URL).resolve("updates.xml")

  override fun computePatchUrl(from: BuildNumber, to: BuildNumber): Url {
    return Urls.newFromEncoded(UPDATE_BASE_URL).resolve(getPatchFileName(from, to))
  }

  private fun getPatchFileName(from: BuildNumber, to: BuildNumber): String {
    val suffix = getPatchSuffix(isMac = SystemInfo.isMac, isWindows = SystemInfo.isWindows, isUnix = SystemInfo.isUnix,
                                isAarch64 = SystemInfo.isAarch64)
    return "${from.asString()}-${to.asStringWithoutProductCode()}-patch-$suffix"
  }

  //  mac_arm, mac_x86, win_x86 and unix_x86 are the only supported OS architectures at this time.
  @VisibleForTesting
  fun getPatchSuffix(isMac: Boolean, isWindows: Boolean, isUnix: Boolean, isAarch64: Boolean): String {
    return when {
      isMac -> if (isAarch64) "mac_arm.jar" else "mac.jar"
      isWindows -> "win.jar"
      isUnix -> "unix.jar"
      // propagate error to the user instead of sending an invalid request to our server
      else -> error("Unrecognized os: ${SystemInfo.OS_NAME} and architecture: ${SystemInfo.OS_ARCH})")
    }
  }

  override val downloadPageUrl: Url
    get() = Urls.newFromEncoded("https://developer.android.com/r/studio-ui/download-stable")

  override val gettingStartedPageUrl: Url
    get() = Urls.newFromEncoded("http://developer.android.com/r/studio-ui/menu-start.html")

  override val whatIsNewPageUrl: Url
    get() = Urls.newFromEncoded("https://developer.android.com/r/studio-ui/menu-whats-new.html")

  override val youTubeChannelUrl: Url
    get() = Urls.newFromEncoded("https://www.youtube.com/c/AndroidDevelopers")

  // Help topics dispatched via HelpManager should generally go to JetBrains pages (https://www.jetbrains.com/help/idea).
  override val helpPageUrl: ((topicId: String) -> Url)?
    get() = jetbrainsUrls.helpPageUrl

  // The keymap reference cards are hosted by JetBrains (https://www.jetbrains.com/idea/docs/IntelliJIDEA_ReferenceCard.pdf).
  override val keyboardShortcutsPdfUrl: Url
    get() = jetbrainsUrls.keyboardShortcutsPdfUrl
}