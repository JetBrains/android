/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.SdkConstants
import com.android.repository.api.ProgressIndicatorAdapter
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.ui.ApplicationUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ModalityState
import java.io.File

/**
 * Android SDK installable component.
 */
class AndroidSdk(installUpdates: Boolean) : InstallableComponent(
  "Android SDK", """
    The collection of Android platform APIs, tools and utilities that enables you to debug, profile, and compile your apps.
    The setup wizard will update your current Android SDK installation (if necessary) or install a new version.
  """.trimIndent(),
  installUpdates) {
  /**
   * Find latest build tools revision. Versions compatible with the selected platforms will be installed by the platform components.
   * @return The Revision of the latest build tools package, or null if no remote build tools packages are available.
   */
  private val latestCompatibleBuildToolsPath: String?
    get() = sdkHandler!!
      .getLatestRemotePackageForPrefix(SdkConstants.FD_BUILD_TOOLS, null, false, object : ProgressIndicatorAdapter() {})
      ?.path

  override val requiredSdkPackages: Collection<String>
    get() = getRequiredSdkPackages(isChromeOSAndIsNotHWAccelerated())

  @VisibleForTesting
  fun getRequiredSdkPackages(isChromeOSAndIsNotHWAccelerated: Boolean): Collection<String> = sequence {
    yield(SdkConstants.FD_EMULATOR.takeIf { !isChromeOSAndIsNotHWAccelerated })
    yield(SdkConstants.FD_PLATFORM_TOOLS)
    yield(latestCompatibleBuildToolsPath)
  }.filterNotNull().toList()

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    // Nothing to do, having components installed is enough
  }

  override fun isOptionalForSdkLocation(): Boolean = false

}

fun setAndroidSdkLocation(sdkLocation: File) {
  ApplicationUtils.invokeWriteActionAndWait(ModalityState.any()) {
    IdeSdks.getInstance().setAndroidSdkPath(sdkLocation)
  }
}