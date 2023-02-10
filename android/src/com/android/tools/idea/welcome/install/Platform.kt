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
package com.android.tools.idea.welcome.install

import com.android.SdkConstants
import com.android.repository.Revision
import com.android.repository.api.RemotePackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.getFullReleaseName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.welcome.wizard.deprecated.InstallComponentsPath.findLatestPlatform

/**
 *
 * Install Android SDK components for developing apps targeting Android platform.
 *
 * Default selection logic:
 *  * If the component of this kind are already installed, they cannot be
 * unchecked (e.g. the wizard will not uninstall them)
 *  * If SDK does not have any platforms installed (or this is a new
 * SDK installation), then only the latest platform will be installed.
 */
class Platform(
  name: String,
  description: String,
  private val myVersion: AndroidVersion,
  private val myIsDefaultPlatform: Boolean,
  installUpdates: Boolean
) : InstallableComponent(name, description, installUpdates) {
  override val requiredSdkPackages: Collection<String>
    get() {
      val requests = mutableListOf(DetailsTypes.getPlatformPath(myVersion))
      findLatestCompatibleBuildTool()?.let {
        requests.add(it)
      }
      return requests
    }

  override val optionalSdkPackages: Collection<String>
    get() = listOf(DetailsTypes.getSourcesPath(myVersion))

  private fun findLatestCompatibleBuildTool(): String? {
    var revision: Revision? = null
    var path: String? = null
    for (remote in repositoryPackages.remotePackages.values) {
      if (!remote.path.startsWith(SdkConstants.FD_BUILD_TOOLS)) {
        continue
      }
      val testRevision = remote.version
      if (testRevision.major == myVersion.apiLevel && (revision == null || testRevision > revision)) {
        revision = testRevision
        path = remote.path
      }
    }
    return path
  }

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {}
  public override fun isOptionalForSdkLocation(): Boolean {
    val locals = getInstalledPlatformVersions(sdkHandler)
    if (locals.isEmpty()) {
      return !myIsDefaultPlatform
    }
    for (androidVersion in locals) {
      // No unchecking if the platform is already installed. We can update but not remove existing platforms
      val apiLevel = androidVersion.apiLevel
      if (myVersion.featureLevel == apiLevel) {
        return false
      }
    }
    return true
  }

  public override fun isSelectedByDefault(): Boolean = false

  companion object {
    private fun getLatestPlatform(remotePackages: Map<String?, RemotePackage>?, installUpdates: Boolean): Platform? {
      val latest = findLatestPlatform(remotePackages, true)
      if (latest != null) {
        val version = (latest.typeDetails as DetailsTypes.PlatformDetailsType).androidVersion
        val versionName = version.getFullReleaseName(includeApiLevel = true, includeCodeName = true)
        val description = "Android platform libraries for targeting platform: $versionName"
        return Platform(versionName, description, version, !version.isPreview, installUpdates)
      }
      return null
    }

    private fun getInstalledPlatformVersions(handler: AndroidSdkHandler?): List<AndroidVersion> {
      val result = mutableListOf<AndroidVersion>()
      if (handler != null) {
        val packages = handler.getSdkManager(StudioLoggerProgressIndicator(Platform::class.java)).packages
        for (p in packages.localPackages.values) {
          if (p.typeDetails is DetailsTypes.PlatformDetailsType) {
            result.add((p.typeDetails as DetailsTypes.PlatformDetailsType).androidVersion)
          }
        }
      }
      return result
    }

    fun createSubtree(remotePackages: Map<String?, RemotePackage>?, installUpdates: Boolean): ComponentTreeNode? {
      // Previously we also installed a preview platform, but no longer (see http://b.android.com/175343 for more).
      val latestPlatform = getLatestPlatform(remotePackages, installUpdates) ?: return null
      return ComponentCategory("Android SDK Platform", "SDK components for creating applications for different Android platforms", listOf(latestPlatform))
    }
  }
}