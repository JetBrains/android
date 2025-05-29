/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.platform

import com.android.repository.api.ProgressIndicator
import com.android.repository.api.UpdatablePackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.getFullApiName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.platform.GradleBuildSettings.getRecommendedBuildToolsRevision
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import java.nio.file.Path
import kotlin.math.max

/**
 * Lists the available Android versions from local and statically-defined sources. The list can be
 * filtered by min sdk level and form factor. It is also possible to query the list of packages that
 * the system needs to install to satisfy the requirements of an API level.
 */
class AndroidVersionsInfo(
  private val targetProvider: () -> Array<IAndroidTarget> = { loadInstalledCompilationTargets() }
) {
  /**
   * The list of known Android versions: 1..HIGHEST_KNOWN_STABLE_API, plus any versions found in
   * [targetProvider]. No remote network connection is needed.
   */
  private val knownTargetVersions: List<VersionItem> by lazy {
    // Load the local definitions of the android compilation targets.
    val installedPlatformTargets = targetProvider.invoke().filter { it.isPlatform }
    val (installedStableTargets, installedPreviewTargets) =
      installedPlatformTargets.partition { !it.version.isPreview }
    buildList {
      val maxInstalledStableVersion =
        installedStableTargets.maxOfOrNull { it.version.androidApiLevel.majorVersion } ?: 0
      val maxStableVersion = max(HIGHEST_KNOWN_STABLE_API, maxInstalledStableVersion)
      for (i in 1..maxStableVersion) {
        add(VersionItem.fromStableVersion(i))
      }

      installedPreviewTargets.forEach { add(VersionItem.fromAndroidVersion(it.version)) }
    }
  }

  /**
   * Gets the list of known Android versions for the given form factor that are greater than or
   * equal to [minSdkLevel].
   */
  fun getKnownTargetVersions(formFactor: FormFactor, minSdkLevel: Int): List<VersionItem> {
    val minSdkLevel = minSdkLevel.coerceAtLeast(formFactor.minOfflineApiLevel)
    val maxSdkLevel =
      if (formFactor.hasUpperLimitForMinimumSdkSelection) formFactor.maxOfflineApiLevel
      else Int.MAX_VALUE
    return knownTargetVersions.filter {
      (formFactor.isSupported(it.minApiLevel) && it.minApiLevel in minSdkLevel..maxSdkLevel) ||
        it.isPreview
    }
  }

  fun loadInstallPackageList(installItems: List<VersionItem>): List<UpdatablePackage> {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    val requestedPaths = buildSet {
      // Install build tools, if not already installed
      add(DetailsTypes.getBuildToolsPath(getRecommendedBuildToolsRevision(sdkHandler, REPO_LOG)))
      // Install the default platform, if that's the one that will be used.
      if (installItems.any { it.compileSdk == AndroidVersion(HIGHEST_KNOWN_STABLE_API, 0) }) {
        add(DetailsTypes.getPlatformPath(AndroidVersion(HIGHEST_KNOWN_STABLE_API, 0)))
      }
    }

    return getPackageList(requestedPaths, sdkHandler)
  }

  data class VersionItem(val minSdk: AndroidVersion, val compileSdk: AndroidVersion) {
    val minApiLevel: Int
      get() = minSdk.featureLevel

    val label: String
      get() = minSdk.getFullApiName(includeReleaseName = true, includeCodeName = true)

    val isPreview: Boolean
      get() = minSdk.isPreview

    override fun toString(): String = label

    fun withCompileSdk(compileSdk: AndroidVersion): VersionItem =
      // minSdk must be <= compileSdk; adjust it if necessary.
      copy(minSdk = minSdk.coerceAtMost(compileSdk), compileSdk = compileSdk)

    companion object {
      fun fromAndroidVersion(minApi: AndroidVersion): VersionItem {
        // By default, compileSdk is the newest supported SDK, but if the specified version is newer
        // or a preview, use it.
        val newProjectsCompileSdkVersion =
          AndroidVersion(StudioFlags.NPW_COMPILE_SDK_VERSION.get(), 0)
        val compileApi =
          if (minApi.isPreview || minApi > newProjectsCompileSdkVersion) minApi
          else newProjectsCompileSdkVersion
        return VersionItem(minSdk = minApi, compileSdk = compileApi)
      }

      fun fromStableVersion(minSdkVersion: Int): VersionItem =
        fromAndroidVersion(AndroidVersion(minSdkVersion, 0))
    }
  }
}

private val REPO_LOG: ProgressIndicator =
  StudioLoggerProgressIndicator(AndroidVersionsInfo::class.java)

val sdkManagerLocalPath: Path?
  get() = IdeSdks.getInstance().androidSdkPath?.toPath()

/** Returns a list of android compilation targets (platforms and add-on SDKs). */
private fun loadInstalledCompilationTargets(): Array<IAndroidTarget> =
  AndroidSdks.getInstance()
    .tryToChooseSdkHandler()
    .getAndroidTargetManager(REPO_LOG)
    .getTargets(REPO_LOG)
    .filter { it.isPlatform || it.additionalLibraries.isNotEmpty() }
    .toTypedArray()

private fun getPackageList(
  requestedPaths: Collection<String>,
  sdkHandler: AndroidSdkHandler,
): List<UpdatablePackage> {
  val packages = sdkHandler.getRepoManagerAndLoadSynchronously(REPO_LOG).packages
  val requestedPackages =
    requestedPaths.mapNotNull { packages.consolidatedPkgs[it] }.filter { it.hasRemote() }

  return try {
    SdkQuickfixUtils.resolve(requestedPackages, packages)
  } catch (e: PackageResolutionException) {
    REPO_LOG.logError("Error Resolving Packages", e)
    listOf()
  }
}
