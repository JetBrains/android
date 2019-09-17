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
import com.android.repository.io.FileOpUtils
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.wizard.dynamic.ScopedStateStore
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.SystemInfo

/**
 * Android SDK installable component.
 */
class AndroidSdk(store: ScopedStateStore, installUpdates: Boolean) : InstallableComponent(
  store, "Android SDK",
  """
    The collection of Android platform APIs, tools and utilities that enables you to debug, profile, and compile your apps.
    The setup wizard will update your current Android SDK installation (if necessary) or install a new version.
  """.trimIndent(),
  installUpdates, FileOpUtils.create()) {
  /**
   * Find latest build tools revision. Versions compatible with the selected platforms will be installed by the platform components.
   * @return The Revision of the latest build tools package, or null if no remote build tools packages are available.
   */
  private val latestCompatibleBuildToolsPath: String?
    get() = sdkHandler!!.getLatestRemotePackageForPrefix(SdkConstants.FD_BUILD_TOOLS, false, object : ProgressIndicatorAdapter() {})?.path

  override val requiredSdkPackages: Collection<String>
    get() = getRequiredSdkPackages(SystemInfo.isChromeOS)

  @VisibleForTesting
  fun getRequiredSdkPackages(chromeOs: Boolean): Collection<String> = sequence {
    yield(SdkConstants.FD_EMULATOR.takeIf { !chromeOs })
    yield(SdkConstants.FD_PLATFORM_TOOLS)
    yield(latestCompatibleBuildToolsPath)
  }.filterNotNull().toList()

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    // Nothing to do, having components installed is enough
  }

  override fun isOptionalForSdkLocation(): Boolean = false
}
