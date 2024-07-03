/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.SdkConstants
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.serverflags.protos.RecommendedVersions
import com.google.wireless.android.sdk.stats.ProductDetails
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateSettings

/**
 * Verifies an Android Gradle project, does not have any modules that violate the compatibility rule between the compile sdk version
 * specified in the module's build.gradle file and the version of Android Studio being used.
 * For example, compileSdk 34 and AS Giraffe are incompatible. For more extensive compatibility info, refer to
 * https://developer.android.com/studio/releases#api-level-support
 *
 * In case of incompatibility, a modal dialog will be surfaced prompting the user to upgrade AS version
 */
class AndroidSdkCompatibilityChecker {

  fun checkAndroidSdkVersion(importedModules: Collection<DataNode<GradleAndroidModelData>>,
                             project: Project,
                             serverFlag: MutableMap<String, RecommendedVersions>?,
                             maxRecommendedCompileSdk: AndroidVersion = MAX_RECOMMENDED_COMPILE_SDK_VERSION) {
    if (StudioUpgradeReminder(project).shouldAsk().not()) return

    val modulesViolatingSupportRules = importedModules.mapNotNull {
      val androidProject = it.data.androidProject
      val moduleName = it.data.moduleName

      val compileTargetSdk: String = androidProject.compileTarget
      val version: AndroidVersion? = AndroidTargetHash.getPlatformVersion(compileTargetSdk)
      return@mapNotNull version?.let { sdkVersion ->
        if (sdkVersion.compareTo(maxRecommendedCompileSdk.apiLevel, maxRecommendedCompileSdk.codename) > 0) {
          Pair(moduleName, sdkVersion)
        } else {
          null
        }
      }
    }

    if (modulesViolatingSupportRules.isEmpty()) return

    // If multiple modules violate the rules, recommending upgrading to the highest one
    val highestViolatingSdkVersion: AndroidVersion = modulesViolatingSupportRules.map { it.second }
                                                       .maxWithOrNull(compareBy({ it.apiLevel }, { it.codename })) ?: return

    val recommendation = if (highestViolatingSdkVersion.isPreview) {
      highestViolatingSdkVersion.codename?.let { previewName -> serverFlag?.get(previewName) }
    } else {
      serverFlag?.get(highestViolatingSdkVersion.apiLevel.toString())
    } ?: return


    val (recommendedVersion, potentialFallbackVersion) = when (getChannelFromUpdateSettings()) {
      ProductDetails.SoftwareLifeCycleChannel.CANARY -> Pair(recommendation.canaryChannel, null)
      ProductDetails.SoftwareLifeCycleChannel.BETA -> Pair(
        recommendation.betaRcChannel,
        recommendation.canaryChannel.takeIf { it.versionReleased }
      )
      ProductDetails.SoftwareLifeCycleChannel.STABLE -> Pair(
        recommendation.stableChannel,
        recommendation.betaRcChannel.takeIf { it.versionReleased } ?: recommendation.canaryChannel.takeIf { it.versionReleased }
      )
      else -> return
    }

    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        val dialog = AndroidSdkCompatibilityDialog(
          project,
          recommendedVersion,
          potentialFallbackVersion,
          modulesViolatingSupportRules
        )
        dialog.show()
      }
    }
  }

  private fun getChannelFromUpdateSettings(): ProductDetails.SoftwareLifeCycleChannel {
    return when (UpdateSettings.getInstance().selectedChannelStatus) {
      ChannelStatus.EAP -> ProductDetails.SoftwareLifeCycleChannel.CANARY
      ChannelStatus.MILESTONE -> ProductDetails.SoftwareLifeCycleChannel.DEV
      ChannelStatus.BETA -> ProductDetails.SoftwareLifeCycleChannel.BETA
      ChannelStatus.RELEASE -> ProductDetails.SoftwareLifeCycleChannel.STABLE
      else -> ProductDetails.SoftwareLifeCycleChannel.UNKNOWN_LIFE_CYCLE_CHANNEL
    }
  }

  companion object {
    val MAX_RECOMMENDED_COMPILE_SDK_VERSION: AndroidVersion = SdkConstants.MAX_SUPPORTED_ANDROID_PLATFORM_VERSION
    const val MAX_NUM_OF_MODULES = 5

    fun getInstance(): AndroidSdkCompatibilityChecker {
      return ApplicationManager.getApplication().getService(AndroidSdkCompatibilityChecker::class.java)
    }
  }

  class StudioUpgradeReminder(val project: Project) {
    private val doNotShowAgainPropertyString = "studio.upgrade.do.not.show.again"

    var doNotAskAgainIdeLevel: Boolean
      get() = PropertiesComponent.getInstance().getBoolean(doNotShowAgainPropertyString, false)
      set(value) = PropertiesComponent.getInstance().setValue(doNotShowAgainPropertyString, value)
    var doNotAskAgainProjectLevel: Boolean
      get() = PropertiesComponent.getInstance(project).getBoolean(doNotShowAgainPropertyString, false)
      set(value) = PropertiesComponent.getInstance(project).setValue(doNotShowAgainPropertyString, value)

    fun shouldAsk(): Boolean {
      return doNotAskAgainProjectLevel.not() || doNotAskAgainIdeLevel.not()
    }
  }
}