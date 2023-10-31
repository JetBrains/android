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

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.ide.impl.customization.LegacyExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.annotations.VisibleForTesting

class AndroidStudioResourceUrls : ExternalProductResourceUrls by LegacyExternalProductResourceUrls() {
  companion object {
    const val UPDATE_URL = "https://dl.google.com/android/studio/patches/updates.xml"
    const val PATCH_URL = "https://dl.google.com/android/studio/patches/"
    /**
      This environment variable is only used by the e2e [com.android.tools.idea.UpdateTest]
    */
    const val UPDATE_ENV_VAR = "AS_UPDATE_URL"
  }

  override val updateMetadataUrl: Url
    get() {
      return if (System.getenv("AS_UPDATE_URL") != null) {
         Urls.newFromEncoded(System.getenv("AS_UPDATE_URL") + "/updates.xml")
      } else {
        Urls.newFromEncoded(UPDATE_URL)
      }
    }

  override fun computePatchUrl(from: BuildNumber, to: BuildNumber): Url? {
    val url = System.getenv(UPDATE_ENV_VAR) ?: PATCH_URL
    val patchFile = getPatchFileName(from, to) ?: return null
    return Urls.newFromEncoded(url).resolve(patchFile)
  }

  private fun getPatchFileName(from: BuildNumber, to: BuildNumber): String? {
    val product = ApplicationInfo.getInstance().build.productCode
    val suffix = getPatchSuffix(isMac = SystemInfo.isMac, isWindows = SystemInfo.isWindows, isUnix = SystemInfo.isUnix,
                                isAarch = SystemInfo.isAarch64) ?: return null
    return "${product}-${from.asStringWithoutProductCode()}-${to.asStringWithoutProductCode()}-patch-$suffix"
  }

  //  mac_arm, mac_x86, win_x86 and unix_x86 are the only supported OS architectures at this time.
  @VisibleForTesting
  fun getPatchSuffix(isMac: Boolean, isWindows: Boolean, isUnix: Boolean, isAarch: Boolean): String {
    return when {
      isMac -> if (isAarch) "mac_arm.jar" else "mac.jar"
      isWindows -> "win.jar"
      isUnix -> "unix.jar"
      // propagate error to the user instead of sending an invalid request to our server
      else -> error("Unrecognized os: ${SystemInfo.OS_NAME} and architecture: ${SystemInfo.OS_ARCH})")
    }
  }
}