/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.SdkConstants
import com.android.repository.api.RepoManager
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.getFullApiName
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import org.jetbrains.android.sdk.AndroidSdkUtils.getTargetLabel
import org.jetbrains.annotations.VisibleForTesting

data class AndroidSdkSuggestions(
  val minSdks: List<ValueDescriptor<String>>,
  val targetSdks: List<ValueDescriptor<String>>,
  val maxSdks: List<ValueDescriptor<Int>>,
  val buildTools: List<ValueDescriptor<String>>,
  val compileSdks: List<ValueDescriptor<String>>,
  val ndks: List<ValueDescriptor<String>>)

private fun knownStableAndroidVersions() =
  (SdkVersionInfo.LOWEST_ACTIVE_API .. SdkVersionInfo.HIGHEST_KNOWN_API).map { AndroidVersion(it, 0) }.toSet()

fun androidSdkSuggestions(): AndroidSdkSuggestions {
  val sdkHandler = AndroidSdks.getInstance().tryToChooseAndroidSdk()?.sdkHandler
  return if (sdkHandler != null) {
    val logger = StudioLoggerProgressIndicator(PsProductFlavor::class.java)
    androidSdkSuggestions(sdkHandler.getRepoManagerAndLoadSynchronously(logger), sdkHandler.getAndroidTargetManager(logger).getTargets(logger), knownStableAndroidVersions())
  }
  else {
    AndroidSdkSuggestions(
      minSdks = listOf(),
      targetSdks = listOf(),
      maxSdks = listOf(),
      compileSdks = listOf(),
      buildTools = listOf(),
      ndks = listOf())
  }
}

@VisibleForTesting
fun androidSdkSuggestions(sdkManager: RepoManager, targets: Collection<IAndroidTarget>, knownAndroidVersions: Set<AndroidVersion>): AndroidSdkSuggestions {
  fun platformName(target: IAndroidTarget) =
    if (target.isPlatform)
      if (target.version == AndroidVersion(36, 0))
        "36" // TODO(b/411099168) Can this case be simplified?
      else
        target.version.apiStringWithExtension.takeIf { it.toIntOrNull() != null } ?: target.version.platformHashString
    else
      AndroidTargetHash.getAddonHashString(target.vendor, target.name, target.version)

  val buildToolsLocalPackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_BUILD_TOOLS)
  val ndkLocalPackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_NDK_SIDE_BY_SIDE)

  val buildToolsMap = buildToolsLocalPackages.map { it.version.toString() }.toSet()
  val compiledApisMap = targets.associate { platformName(it) to getTargetLabel(it) }
  val ndksMap = ndkLocalPackages.map { it.version.toString() }.toSet()

  val majorVersions = buildSet {
    addAll(knownAndroidVersions)
    targets.stream().filter { it.isPlatform }.map { it.version }.forEach { add(it) }
  }.filter { it.isBaseExtension && (it.androidApiLevel.minorVersion == 0 || it.isPreview) }
    .sorted()

  fun AndroidVersion.minOrTargetPlatformName(): String = if (isPreview) platformHashString else "${androidApiLevel.majorVersion}"
  fun AndroidVersion.fullApiName() = getFullApiName(includeReleaseName = true, includeCodeName = true, includeMinorVersion = false)

  return AndroidSdkSuggestions(
    minSdks = majorVersions.map { ValueDescriptor(it.minOrTargetPlatformName(), it.fullApiName()) },
    targetSdks = majorVersions.map { ValueDescriptor(it.minOrTargetPlatformName(), it.fullApiName()) }, // TODO(b/414551906): Raise the lower limit of what is suggested here?
    maxSdks = majorVersions.filter { !it.isPreview }.map { ValueDescriptor(it.androidApiLevel.majorVersion, it.fullApiName()) },
    compileSdks = compiledApisMap.map { ValueDescriptor(value = it.key, description = it.value) },
    buildTools = buildToolsMap.map { ValueDescriptor(value = it, description = null) },
    ndks = ndksMap.map { ValueDescriptor(value = it, description = null) }
  )
}