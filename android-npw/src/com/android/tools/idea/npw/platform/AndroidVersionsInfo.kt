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
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SystemImageTags
import com.android.sdklib.getFullApiName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.meta.DetailsTypes.AddonDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.platform.GradleBuildSettings.getRecommendedBuildToolsRevision
import com.android.tools.idea.npw.invokeLater
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.math.max

/**
 * Lists the available Android Versions from local, remote, and statically-defined sources.
 * The list can be filtered by min sdk level and a callback mechanism allows information to be provided asynchronously.
 * It is also possible to query the list of packages that the system needs to install to satisfy the requirements of an API level.
 */
class AndroidVersionsInfo(
  private val targetProvider: () -> Array<IAndroidTarget> = { loadInstalledCompilationTargets() }
) {
  private lateinit var knownTargetVersions: List<VersionItem>

  /**
   * Load the list of known Android Versions. The list is made of Android Studio pre-known Android versions, and querying
   * the SDK manager for extra installed versions (can be third party SDKs). No remote network connection is needed.
   */
  fun loadLocalVersions() {
    // Load the local definitions of the android compilation targets.
    val installedPlatformTargets = targetProvider.invoke().filter { it.isPlatform }
    val (installedStableTargets, installedPreviewTargets) =
      installedPlatformTargets.partition { !it.version.isPreview }
    knownTargetVersions = sequence {
      val maxInstalledStableVersion = installedStableTargets.maxOfOrNull { it.version.androidApiLevel.majorVersion } ?: 0
      val maxStableVersion = max(HIGHEST_KNOWN_STABLE_API, maxInstalledStableVersion)
      for (i in 1..maxStableVersion) {
        yield(VersionItem.fromStableVersion(i))
      }

      installedPreviewTargets.forEach { yield(VersionItem.fromAndroidVersion(it.version)) }
    }.toList()
  }

  /**
   * Gets the list of known Android versions. The list can be loaded by calling [loadLocalVersions] and/or [loadRemoteTargetVersions].
   */
  fun getKnownTargetVersions(formFactor: FormFactor, minSdkLevel: Int): MutableList<VersionItem> {
    val minSdkLevel = minSdkLevel.coerceAtLeast(formFactor.minOfflineApiLevel)
    val maxSdkLevel = if (formFactor.hasUpperLimitForMinimumSdkSelection) formFactor.maxOfflineApiLevel else Int.MAX_VALUE
    return knownTargetVersions.filter {
      formFactor.isAvailable(minSdkLevel .. maxSdkLevel, it.minApiLevel) || it.isPreview
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

    sdkHandler.getRepoManager(REPO_LOG).load(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      listOf(onLocalComplete),
      listOf(onComplete),
      listOf(onError),
      StudioProgressRunner(false, false, "Refreshing Targets", null),
      StudioDownloader(), StudioSettingsController.getInstance())
  }

  data class VersionItem(
    val minSdk: AndroidVersion,
    val compileSdk: AndroidVersion,
  ) {
    /** The equivalent integer API level that will be compiled against. (For previews, this is the "feature level".) */
    val buildApiLevel: Int
      get() = compileSdk.featureLevel
    /** The compile SDK version to use in generated build.gradle files. This must be parseable by AndroidVersion.fromString(). */
    val buildApiLevelStr: String
      get() = compileSdk.apiStringWithExtension
    val minApiLevel: Int
      get() = minSdk.featureLevel
    val minApiLevelStr: String // Can be a number or a codename (eg "L", "N", etc)
      get() = minSdk.majorVersion.apiString
    val targetApiLevel: Int
      get() = buildApiLevel
    val targetApiLevelStr: String
      get() = compileSdk.majorVersion.apiString

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
        // By default, compileSdk is the newest supported SDK, but if the specified version is newer or
        // a preview, use it.
        val newProjectsCompileSdkVersion = AndroidVersion(StudioFlags.NPW_COMPILE_SDK_VERSION.get(), 0)
        val compileApi = if (minApi.isPreview || minApi > newProjectsCompileSdkVersion) minApi else newProjectsCompileSdkVersion
        return VersionItem(minSdk = minApi, compileSdk = compileApi)
      }

      fun fromStableVersion(minSdkVersion: Int): VersionItem = fromAndroidVersion(AndroidVersion(minSdkVersion, 0))
    }
  }
}

private val REPO_LOG: ProgressIndicator = StudioLoggerProgressIndicator(AndroidVersionsInfo::class.java)
private val NO_MATCH: IdDisplay = IdDisplay.create("no_match", "No Match")

val sdkManagerLocalPath: Path? get() = IdeSdks.getInstance().androidSdkPath?.toPath()

/**
 * Returns a list of android compilation targets (platforms and add-on SDKs).
 */
private fun loadInstalledCompilationTargets(): Array<IAndroidTarget> =
  AndroidSdks.getInstance().tryToChooseSdkHandler().getAndroidTargetManager(REPO_LOG).getTargets(REPO_LOG).filter {
    it.isPlatform || it.additionalLibraries.isNotEmpty()
  }.toTypedArray()

private fun getPackageList(requestedPaths: Collection<String>,
                           sdkHandler: AndroidSdkHandler): List<UpdatablePackage> {
  val packages = sdkHandler.getRepoManagerAndLoadSynchronously(REPO_LOG).packages
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
  p.typeDetails is ApiDetailsType && doFilter(formFactor, minSdkLevel .. Int.MAX_VALUE, getTag(p), p.getFeatureLevel())

private fun doFilter(formFactor: FormFactor, sdkLevelRange: IntRange, tag: IdDisplay?, targetSdkLevel: Int): Boolean =
  formFactor.isSupported(tag, targetSdkLevel) && targetSdkLevel in sdkLevelRange

private fun RepoPackage.getFeatureLevel(): Int = getAndroidVersion(this).featureLevel

private fun FormFactor.isAvailable(sdkLevelRange: IntRange, targetSdkLevel: Int): Boolean =
  doFilter(this, sdkLevelRange, defaultTag(), targetSdkLevel)

private fun FormFactor.defaultTag() = when (this) {
  FormFactor.MOBILE -> SystemImageTags.DEFAULT_TAG
  FormFactor.WEAR -> SystemImageTags.WEAR_TAG
  FormFactor.TV -> SystemImageTags.ANDROID_TV_TAG
  FormFactor.AUTOMOTIVE -> SystemImageTags.AUTOMOTIVE_TAG
  FormFactor.XR -> SystemImageTags.XR_TAG
}

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