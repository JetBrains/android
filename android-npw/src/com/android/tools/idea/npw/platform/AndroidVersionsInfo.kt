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

import com.android.SdkConstants.CPU_ARCH_INTEL_ATOM
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.repository.api.RepoPackage
import com.android.repository.api.UpdatablePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_COMPILE_SDK_VERSION
import com.android.sdklib.getFullApiName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.meta.DetailsTypes.AddonDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision
import com.android.tools.idea.npw.invokeLater
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import java.io.File
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.math.max

/**
 * Lists the available Android Versions from local, remote, and statically-defined sources.
 * The list can be filtered by min sdk level and a callback mechanism allows information to be provided asynchronously.
 * It is also possible to query the list of packages that the system needs to install to satisfy the requirements of an API level.
 */
class AndroidVersionsInfo {
  private lateinit var knownTargetVersions: List<VersionItem>

  /**
   * Load the list of known Android Versions. The list is made of Android Studio pre-known Android versions, and querying
   * the SDK manager for extra installed versions (can be third party SDKs). No remote network connection is needed.
   */
  fun loadLocalVersions() {
    // Load the local definitions of the android compilation targets.
    val installedPlatformAndAddonTargets = loadInstalledCompilationTargets()
    val (installedStableTargets, installedPreviewAndAddonTargets) =
      installedPlatformAndAddonTargets.partition { it.isPlatform && !it.version.isPreview }
    knownTargetVersions = sequence {
      // Stable versions.
      val maxStableVersion = max(HIGHEST_KNOWN_STABLE_API, installedStableTargets.maxOfOrNull { it.version.apiLevel } ?: 0)
      for (i in 1..maxStableVersion) {
        yield(VersionItem.fromStableVersion(i))
      }
      // Installed previews and add-ons
      installedPreviewAndAddonTargets.forEach { yield(VersionItem.fromAndroidTarget(it)) }
    }.toList()
  }

  /**
   * Gets the list of known Android versions. The list can be loaded by calling [loadLocalVersions] and/or [loadRemoteTargetVersions].
   */
  fun getKnownTargetVersions(formFactor: FormFactor, minSdkLevel: Int): MutableList<VersionItem> {
    val minSdkLevel = minSdkLevel.coerceAtLeast(formFactor.minOfflineApiLevel)
    return knownTargetVersions.filter {
      formFactor.isAvailable(minSdkLevel, it.minApiLevel) || it.androidTarget?.version?.isPreview == true
    }.toMutableList()
  }

  fun loadInstallPackageList(installItems: List<VersionItem>): List<UpdatablePackage> {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    val requestedPaths = sequence {
      // Install build tools, if not already installed
      yield(DetailsTypes.getBuildToolsPath(getRecommendedBuildToolsRevision(sdkHandler, REPO_LOG)))
      // Install the default platform, if that's the one that will be used.
      if (installItems.any { it.buildApiLevelStr == HIGHEST_KNOWN_STABLE_API.toString() }) {
        yield(DetailsTypes.getPlatformPath(AndroidVersion(HIGHEST_KNOWN_STABLE_API, null)))
      }
    }.toSet()

    return getPackageList(requestedPaths, sdkHandler)
  }

  /**
   * Get the list of versions, notably by populating the available values from local, remote, and statically-defined sources.
   */
  fun loadRemoteTargetVersions(formFactor: FormFactor, minSdkLevel: Int, itemsLoadedCallback: Consumer<List<VersionItem>>) {
    val versionItemList = getKnownTargetVersions(formFactor, minSdkLevel)

    fun addPackages(packages: Collection<RepoPackage?>) {
      val sorted = packages.asSequence()
        .filterNotNull()
        .filter { filterPkgDesc(it, formFactor, minSdkLevel.coerceAtLeast(formFactor.minOfflineApiLevel)) }
        .sortedBy { getAndroidVersion(it) }
      var existingApiLevel = -1
      var prevInsertedApiLevel = -1
      var index = -1
      for (info in sorted) {
        val apiLevel = info.getFeatureLevel()
        while (apiLevel > existingApiLevel) {
          existingApiLevel = if (++index < versionItemList.size) versionItemList[index].minApiLevel else Integer.MAX_VALUE
        }
        if (apiLevel != existingApiLevel && apiLevel != prevInsertedApiLevel) {
          versionItemList.add(index++, VersionItem.fromAndroidVersion(getAndroidVersion(info)))
          prevInsertedApiLevel = apiLevel
        }
      }
    }

    fun addOfflineLevels() {
      var existingApiLevel = -1
      var prevInsertedApiLevel = -1
      var index = -1
      val supportedOfflineApiLevels = formFactor.minOfflineApiLevel.coerceAtLeast(minSdkLevel)..formFactor.maxOfflineApiLevel
      supportedOfflineApiLevels.filterNot { formFactor.isSupported(null, it) }.forEach { apiLevel ->
        while (apiLevel > existingApiLevel) {
          existingApiLevel = if (++index < versionItemList.size) versionItemList[index].minApiLevel else Integer.MAX_VALUE
        }
        if (apiLevel != existingApiLevel && apiLevel != prevInsertedApiLevel) {
          versionItemList.add(index++, VersionItem.fromAndroidVersion(AndroidVersion(apiLevel)))
          prevInsertedApiLevel = apiLevel
        }
      }
    }

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    val runCallbacks = Runnable { itemsLoadedCallback.accept(versionItemList) }

    val onComplete = RepoLoadedListener { packages: RepositoryPackages ->
      invokeLater {
        addPackages(packages.newPkgs)
        addOfflineLevels()
        runCallbacks.run()
      }
    }

    // We need to pick up addons that don't have a target created due to the base platform not being installed.
    val onLocalComplete = RepoLoadedListener { packages: RepositoryPackages ->
      invokeLater {
        addPackages(packages.localPackages.values)
      }
    }

    val onError = Runnable {
      invokeLater {
        addOfflineLevels()
        runCallbacks.run()
      }
    }

    sdkHandler.getSdkManager(REPO_LOG).load(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      listOf(onLocalComplete),
      listOf(onComplete),
      listOf(onError),
      StudioProgressRunner(false, false, "Refreshing Targets", null),
      StudioDownloader(), StudioSettingsController.getInstance())
  }

  class VersionItem private constructor(
    val label: String,
    /** The equivalent integer API level that will be compiled against. */
    val buildApiLevel: Int,
    /** The compile SDK version to use in generated build.gradle files */
    val buildApiLevelStr: String,
    val minApiLevel: Int,
    val minApiLevelStr: String, // Can be a number or a Code Name (eg "L", "N", etc)
    val targetApiLevelStr: String,
    // Only present for already installed preview and addons
    val androidTarget: IAndroidTarget?,
  ) {

    val targetApiLevel: Int
      get() = buildApiLevel

    override fun equals(other: Any?): Boolean = other is VersionItem && other.label == label

    override fun hashCode(): Int = label.hashCode()

    override fun toString(): String = label

    companion object {
      fun fromAndroidVersion(version: AndroidVersion): VersionItem {
        // For preview versions or if the requested target is newer than HIGHEST_KNOWN_STABLE_API,
        // use build and target as the given version
        val futureVersion = version.isPreview || version.apiLevel > HIGHEST_KNOWN_STABLE_API

        return VersionItem(
          label = getLabel(version, null),
          androidTarget = null,
          minApiLevel = version.featureLevel,
          minApiLevelStr = version.apiString,
          buildApiLevel = if (futureVersion) version.featureLevel else HIGHEST_KNOWN_STABLE_API,
          buildApiLevelStr = if (futureVersion) version.toBuildApiString() else HIGHEST_KNOWN_STABLE_API.toString(),
          targetApiLevelStr = if(futureVersion) version.apiString else HIGHEST_KNOWN_STABLE_API.toString(),
        )
      }

      fun fromStableVersion(minSdkVersion: Int): VersionItem = fromAndroidVersion(AndroidVersion(minSdkVersion))

      fun fromAndroidTarget(target: IAndroidTarget): VersionItem {
        if (target.isPlatform && !target.version.isPreview) {
          return fromAndroidVersion(target.version)
        }

        return VersionItem(
          label = getLabel(target.version, target),
          androidTarget = target,
          minApiLevel = target.version.featureLevel,
          minApiLevelStr = target.version.apiString,
          buildApiLevel = target.version.featureLevel,
          buildApiLevelStr = AndroidTargetHash.getTargetHashString(target),
          targetApiLevelStr = target.version.apiString,
        )
      }
    }
  }
}

private val REPO_LOG: ProgressIndicator = StudioLoggerProgressIndicator(AndroidVersionsInfo::class.java)
private val NO_MATCH: IdDisplay = IdDisplay.create("no_match", "No Match")

val sdkManagerLocalPath: Path? get() = IdeSdks.getInstance().androidSdkPath?.toPath()

private fun getLabel(version: AndroidVersion, target: IAndroidTarget?): String {

  if (target != null && !target.isPlatform) {
    return AndroidTargetHash.getTargetHashString(target)
  }

  return version.getFullApiName(includeReleaseName = true, includeCodeName = true)
}


/**
 * Returns a list of android compilation targets (platforms and add-on SDKs).
 */
private fun loadInstalledCompilationTargets(): Array<IAndroidTarget> =
  AndroidSdks.getInstance().tryToChooseSdkHandler().getAndroidTargetManager(REPO_LOG).getTargets(REPO_LOG).filter {
    it.isPlatform || it.additionalLibraries.isNotEmpty()
  }.toTypedArray()

private fun getPackageList(requestedPaths: Collection<String>,
                           sdkHandler: AndroidSdkHandler): List<UpdatablePackage> {
  val packages = sdkHandler.getSdkManager(REPO_LOG).packages
  val requestedPackages = requestedPaths.mapNotNull { packages.consolidatedPkgs[it] }.filter { it.hasRemote() }

  return try {
    SdkQuickfixUtils.resolve(requestedPackages, packages)
  }
  catch (e: PackageResolutionException) {
    REPO_LOG.logError("Error Resolving Packages", e)
    listOf()
  }
}

private fun filterPkgDesc(p: RepoPackage, formFactor: FormFactor, minSdkLevel: Int): Boolean =
  p.typeDetails is ApiDetailsType && doFilter(formFactor, minSdkLevel, getTag(p), p.getFeatureLevel())

private fun doFilter(formFactor: FormFactor, minSdkLevel: Int, tag: IdDisplay?, targetSdkLevel: Int): Boolean =
  formFactor.isSupported(tag, targetSdkLevel) && targetSdkLevel >= minSdkLevel

private fun RepoPackage.getFeatureLevel(): Int = getAndroidVersion(this).featureLevel

private fun FormFactor.isAvailable(minSdkLevel: Int, targetSdkLevel: Int): Boolean =
  doFilter(this, minSdkLevel, SystemImage.DEFAULT_TAG, targetSdkLevel)

private fun getAndroidVersion(repoPackage: RepoPackage): AndroidVersion = (repoPackage.typeDetails as ApiDetailsType).androidVersion

/**
 * Return the tag for the specified repository package. We are only interested in 2 package types.
 */
private fun getTag(repoPackage: RepoPackage): IdDisplay? {
  val details = repoPackage.typeDetails
  return when {
    details is AddonDetailsType -> details.tag
    // TODO: support multi-tag
    details is SysImgDetailsType && details.abi == CPU_ARCH_INTEL_ATOM -> details.tags[0]
    else -> NO_MATCH
  }
}

/**
 * Computes a suitable build api string, e.g. "18" for API level 18.
 */
fun AndroidVersion.toBuildApiString() =
  if (isPreview) AndroidTargetHash.getPlatformHashString(this) else apiString