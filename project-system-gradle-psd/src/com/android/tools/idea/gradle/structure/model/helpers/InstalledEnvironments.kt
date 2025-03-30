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
import com.android.sdklib.IAndroidTarget
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import org.jetbrains.android.sdk.AndroidSdkUtils.getTargetLabel

data class InstalledEnvironments(
  val buildTools: List<ValueDescriptor<String>>,
  val androidSdks: List<ValueDescriptor<String>>,
  val compiledApis: List<ValueDescriptor<String>>,
  val ndks: List<ValueDescriptor<String>>)

fun installedEnvironments(): InstalledEnvironments {
  val sdkHandler = AndroidSdks.getInstance().tryToChooseAndroidSdk()?.sdkHandler
  return if (sdkHandler != null) {
    val logger = StudioLoggerProgressIndicator(PsProductFlavor::class.java)
    installedEnvironments(sdkHandler.getSdkManager(logger), sdkHandler.getAndroidTargetManager(logger).getTargets(logger))
  }
  else {
    InstalledEnvironments(androidSdks = listOf(), compiledApis = listOf(), buildTools = listOf(), ndks = listOf())
  }
}

fun installedEnvironments(sdkManager: RepoManager, targets: Collection<IAndroidTarget>): InstalledEnvironments {
  fun platformName(target: IAndroidTarget) =
    if (target.isPlatform)
      if (target.version.isPreview) AndroidTargetHash.getPlatformHashString(target.version) else target.version.apiStringWithExtension
    else
      AndroidTargetHash.getAddonHashString(target.vendor, target.name, target.version)

  val buildToolsLocalPackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_BUILD_TOOLS)
  val ndkLocalPackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_NDK_SIDE_BY_SIDE)

  val buildToolsMap = buildToolsLocalPackages.map { it.version.toString() }.toSet()
  val apisMap = targets.filter { it.isPlatform }.associate { platformName(it) to getTargetLabel(it) }
  val compiledApisMap = targets.associate { platformName(it) to getTargetLabel(it) }
  val ndksMap = ndkLocalPackages.map { it.version.toString() }.toSet()

  return InstalledEnvironments(
    androidSdks = apisMap.map { ValueDescriptor(value = it.key, description = it.value) },
    compiledApis = compiledApisMap.map { ValueDescriptor(value = it.key, description = it.value) },
    buildTools = buildToolsMap.map { ValueDescriptor(value = it, description = null) },
    ndks = ndksMap.map { ValueDescriptor(value = it, description = null) }
  )
}