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
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import org.jetbrains.android.sdk.AndroidSdkUtils.getTargetLabel

data class InstalledEnvironments(
    val buildTools: List<ValueDescriptor<String>>,
    val androidSdks: List<ValueDescriptor<Int>>,
    val compiledApis: List<ValueDescriptor<String>>)

fun installedEnvironments(): InstalledEnvironments {
  val sdkHandler = AndroidSdks.getInstance().tryToChooseAndroidSdk()?.sdkHandler
  return if (sdkHandler != null) {
    val logger = StudioLoggerProgressIndicator(PsProductFlavor::class.java)
    installedEnvironments(sdkHandler.getSdkManager(logger), sdkHandler.getAndroidTargetManager(logger).getTargets(logger))
  }
  else {
    InstalledEnvironments(androidSdks = listOf(), compiledApis = listOf(), buildTools = listOf())
  }
}

fun installedEnvironments(sdkManager: RepoManager, targets: Collection<IAndroidTarget>): InstalledEnvironments {
  fun platformName(target: IAndroidTarget) =
      if (target.isPlatform)
        if (target.version.isPreview) AndroidTargetHash.getPlatformHashString(target.version) else target.version.apiString
      else
        AndroidTargetHash.getAddonHashString(target.vendor, target.name, target.version)

  val localPackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_BUILD_TOOLS)

  val buildToolsMap = localPackages.map { it.version.toString() }.toSet()
  val apisMap = targets.filter { it.isPlatform }.associate { it.version.apiLevel to getTargetLabel(it) }
  val compiledApisMap = targets.associate { platformName(it) to getTargetLabel(it) }

  return InstalledEnvironments(
      androidSdks = apisMap.map { ValueDescriptor(value = it.key, description = it.value) },
      compiledApis = compiledApisMap.map { ValueDescriptor(value = it.key, description = it.value) },
      buildTools = buildToolsMap.map { ValueDescriptor(value = it, description = it) }
  )
}