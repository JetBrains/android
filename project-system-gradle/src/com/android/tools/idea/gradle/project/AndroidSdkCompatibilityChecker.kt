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
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.serverflags.protos.RecommendedVersions
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.UpgradeAndroidStudioDialogStats
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import org.jetbrains.android.util.AndroidBundle


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
                             serverFlag: MutableMap<Int, RecommendedVersions>?) {
    if (StudioUpgradeReminder(project).shouldAsk().not()) return

    val modulesViolatingSupportRules = importedModules.mapNotNull {
      val androidProject = it.data.androidProject
      val moduleName = it.data.moduleName

      val compileTargetSdk: String = androidProject.compileTarget
      val version: AndroidVersion? = AndroidTargetHash.getPlatformVersion(compileTargetSdk)
      return@mapNotNull version?.let { sdkVersion ->
        if (sdkVersion.compareTo(MAX_RECOMMENDED_COMPILE_SDK_VERSION.apiLevel, MAX_RECOMMENDED_COMPILE_SDK_VERSION.codename) > 0) {
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

    val recommendation = serverFlag?.get(highestViolatingSdkVersion.apiLevel) ?: return

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

    // We can't recommend upgrading to the latest version of AS (from the same channel) that will support
    // the required API level
    val content = if (!recommendedVersion.versionReleased) {
      if (potentialFallbackVersion != null) {
        AndroidBundle.message(
          "project.upgrade.studio.notification.body.different.channel.recommendation",
          ApplicationInfo.getInstance().fullVersion,
          potentialFallbackVersion.buildDisplayName
        ) + getAffectedModules(modulesViolatingSupportRules)
      } else {
        AndroidBundle.message(
          "project.upgrade.studio.notification.body.no.recommendation",
          ApplicationInfo.getInstance().fullVersion,
        ) + getAffectedModules(modulesViolatingSupportRules)
      }
    } else {
      AndroidBundle.message(
        "project.upgrade.studio.notification.body.same.channel.recommendation",
        ApplicationInfo.getInstance().fullVersion,
        recommendedVersion.buildDisplayName
      ) + getAffectedModules(modulesViolatingSupportRules)
    }

    ApplicationManager.getApplication().invokeAndWait({
      val dontAskAgain: DialogWrapper.DoNotAskOption = object : DialogWrapper.DoNotAskOption.Adapter() {
        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
          if (isSelected) {
            logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.DO_NOT_ASK_AGAIN)
            StudioUpgradeReminder(project).doNotAskAgainProjectLevel = true
            StudioUpgradeReminder(project).doNotAskAgainIdeLevel = true
          }
        }

        override fun getDoNotShowMessage(): String {
          return DO_NOT_ASK_FOR_PROJECT_BUTTON_TEXT
        }
      }
      val (options, defaultOption) = if(recommendedVersion.versionReleased) {
        Pair(arrayOf(Messages.getCancelButton(), UPDATE_STUDIO_BUTTON_TEXT), 1)
      } else {
        Pair(arrayOf(Messages.getCancelButton()), 0)
      }

      val selection = Messages.showIdeaMessageDialog(
        project,
        content,
        AndroidBundle.message("project.upgrade.studio.notification.title"),
        options,
        defaultOption,
        Messages.getInformationIcon(), dontAskAgain
      )

      when (selection) {
        0 -> logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.CANCEL)
        1 -> {
          logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.UPGRADE_STUDIO)
          UpdateChecker.updateAndShowResult(project)
        }
      }
    }, ModalityState.nonModal())
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

  private fun logEvent(project: Project, action: UpgradeAndroidStudioDialogStats.UserAction) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .withProjectId(project)
        .setKind(AndroidStudioEvent.EventKind.UPGRADE_ANDROID_STUDIO_DIALOG)
        .setUpgradeAndroidStudioDialog(
          UpgradeAndroidStudioDialogStats.newBuilder().apply {
            userAction = action
          }
        )
    )
  }

  private fun getAffectedModules(modules: List<Pair<String, AndroidVersion>>): String {
    val modulesToShow = modules.take(MAX_NUM_OF_MODULES)
    val remainingModules = modules.drop(MAX_NUM_OF_MODULES)

    val content = StringBuilder()
    content.append(
      "Affected modules: " + modulesToShow.joinToString {
        "<br/>'${it.first}' (compileSdk=${it.second.apiStringWithoutExtension})"
      }
    )

    if (remainingModules.isNotEmpty()) {
      content.append(" (and ${remainingModules.size} more)")
    }
    return content.toString()
  }

  companion object {
    val MAX_RECOMMENDED_COMPILE_SDK_VERSION: AndroidVersion = SdkConstants.MAX_SUPPORTED_ANDROID_PLATFORM_VERSION
    const val UPDATE_STUDIO_BUTTON_TEXT = "Upgrade Android Studio"
    const val DO_NOT_ASK_FOR_PROJECT_BUTTON_TEXT = "Don't ask for this project"
    const val CANCEL_BUTTON_TEXT = "Cancel"
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