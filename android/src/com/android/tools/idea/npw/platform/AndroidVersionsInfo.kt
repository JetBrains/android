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
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.meta.DetailsTypes.AddonDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.invokeLater
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.progress.StudioProgressRunner
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import com.android.tools.idea.templates.TemplateUtils.knownVersions
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.util.function.Consumer

/**
 * Lists the available Android Versions from local, remote, and statically-defined sources.
 * The list can be filtered by min sdk level and a callback mechanism allows information to be provided asynchronously.
 * It is also possible to query the list of packages that the system needs to install to satisfy the requirements of an API level.
 */
class AndroidVersionsInfo {
  private lateinit var knownTargetVersions: List<VersionItem>
  private lateinit var installedVersions: Set<AndroidVersion>
  private var highestInstalledApiTarget: IAndroidTarget? = null
  @VisibleForTesting
  val highestInstalledVersion: AndroidVersion?
    get() = highestInstalledApiTarget?.version

  /**
   * Load the list of known Android Versions. The list is made of Android Studio pre-known Android versions, and querying
   * the SDK manager for extra installed versions (can be third party SDKs). No remote network connection is needed.
   */
  fun loadLocalVersions() {
    // Load the local definitions of the android compilation targets.
    val installedCompilationTargets = loadInstalledCompilationTargets()
    val additionalInstalledTargets = installedCompilationTargets.filter { it.version.isPreview || it.additionalLibraries.isNotEmpty() }
    knownTargetVersions = sequence {
      knownVersions.forEachIndexed { i, version ->
        yield(VersionItem(version, i + 1))
      }
      additionalInstalledTargets.forEach { yield(VersionItem(it)) }
    }.toList()

    // Load the installed android versions from the installed SDK.
    installedVersions = additionalInstalledTargets.map { it.version }.toSet()

    highestInstalledApiTarget = installedCompilationTargets
      .filter { it.isPlatform && it.version.featureLevel >= LOWEST_COMPILE_SDK_VERSION && !it.version.isPreview }
      .maxBy { it.version.featureLevel }
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
      if (highestInstalledApiTarget == null || installItems.any {
          it.androidVersion.apiLevel > highestInstalledApiTarget!!.version.apiLevel && !installedVersions.contains(it.androidVersion)
        }) {
        // Let us install the HIGHEST_KNOWN_STABLE_API.
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
          versionItemList.add(index++, VersionItem(getAndroidVersion(info)))
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
          versionItemList.add(index++, VersionItem(AndroidVersion(apiLevel)))
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
      StudioDownloader(), StudioSettingsController.getInstance(), false)
  }

  inner class VersionItem {
    val androidVersion: AndroidVersion
    val label: String
    val minApiLevel: Int
    val minApiLevelStr: String // Can be a number or a Code Name (eg "L", "N", etc)
    var androidTarget: IAndroidTarget? = null
      private set

    @VisibleForTesting
    constructor(androidVersion: AndroidVersion, target: IAndroidTarget? = null) {
      this.androidVersion = androidVersion
      label = getLabel(androidVersion, target)
      androidTarget = target
      minApiLevel = androidVersion.featureLevel
      minApiLevelStr = androidVersion.apiString
    }

    internal constructor(label: String, minApiLevel: Int) {
      androidVersion = AndroidVersion(minApiLevel)
      this.label = label
      this.minApiLevel = minApiLevel
      minApiLevelStr = minApiLevel.toString()
    }

    @VisibleForTesting
    constructor(target: IAndroidTarget) : this(target.version, target)

    val buildApiLevel: Int
      get() = when {
        androidTarget != null && (androidTarget!!.version.isPreview || !androidTarget!!.isPlatform) -> minApiLevel
        (highestInstalledVersion?.featureLevel ?: 0) > HIGHEST_KNOWN_STABLE_API -> highestInstalledVersion!!.featureLevel
        else -> HIGHEST_KNOWN_STABLE_API
      }

    val buildApiLevelStr: String
      get() = when {
        androidTarget == null -> buildApiLevel.toString()
        androidTarget!!.isPlatform -> androidTarget!!.version.toBuildApiString()
        else -> AndroidTargetHash.getTargetHashString(androidTarget!!)
      }

    val targetApiLevel: Int
      get() = buildApiLevel

    val targetApiLevelStr: String
      get() = when {
        buildApiLevel >= HIGHEST_KNOWN_API || androidTarget?.version?.isPreview == true ->
          androidTarget?.version?.apiString ?: buildApiLevel.toString()
        highestInstalledVersion?.featureLevel == buildApiLevel -> highestInstalledVersion!!.apiString
        else -> buildApiLevel.toString()
      }

    override fun equals(other: Any?): Boolean = other is VersionItem && other.label == label

    override fun hashCode(): Int = label.hashCode()

    override fun toString(): String = label
  }
}

private val REPO_LOG: ProgressIndicator = StudioLoggerProgressIndicator(AndroidVersionsInfo::class.java)
private val NO_MATCH: IdDisplay = IdDisplay.create("no_match", "No Match")

val sdkManagerLocalPath: File? get() = IdeSdks.getInstance().androidSdkPath

private fun getLabel(version: AndroidVersion, target: IAndroidTarget?): String {
  val featureLevel = version.featureLevel

  if (featureLevel > HIGHEST_KNOWN_API) {
    return "API $featureLevel: Android ${version.apiString}"  + " (${version.codename} preview)".takeIf { version.isPreview }.orEmpty()
  }

  return when {
      version.isPreview ->
        "API %s: Android %s (%s preview)".format(
          featureLevel,
          SdkVersionInfo.getVersionStringSanitized(featureLevel),
          SdkVersionInfo.getCodeName(featureLevel))
      target == null || target.isPlatform -> SdkVersionInfo.getAndroidName(featureLevel)
      else -> AndroidTargetHash.getTargetHashString(target)
  }
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
    details is SysImgDetailsType && details.abi == CPU_ARCH_INTEL_ATOM -> details.tag
    else -> NO_MATCH
  }
}

/**
 * Computes a suitable build api string, e.g. "18" for API level 18.
 */
fun AndroidVersion.toBuildApiString() =
  if (isPreview) AndroidTargetHash.getPlatformHashString(this) else apiString