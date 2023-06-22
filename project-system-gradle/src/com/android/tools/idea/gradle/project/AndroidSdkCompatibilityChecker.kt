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
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.UpgradeAndroidStudioDialogStats
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.UpdateChecker
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
                             project: Project) {
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

    val content = AndroidBundle.message(
      "project.upgrade.studio.notification.body.without.recommendation",
      ApplicationInfo.getInstance().fullVersion
    ) + getAffectedModules(modulesViolatingSupportRules)

    invokeLater(ModalityState.NON_MODAL) {
      val selection = Messages.showIdeaMessageDialog(
        project,
        content,
        AndroidBundle.message("project.upgrade.studio.notification.title"),
        arrayOf(
          UPDATE_STUDIO_BUTTON.second,
          DO_NOT_ASK_FOR_PROJECT_BUTTON.second
        ),
        0,
        Messages.getWarningIcon(),
        null
      )

      when (selection) {
        UPDATE_STUDIO_BUTTON.first -> {
          logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.UPGRADE_STUDIO)
          UpdateChecker.updateAndShowResult(project)
        }
        DO_NOT_ASK_FOR_PROJECT_BUTTON.first -> {
          logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.DO_NOT_ASK_AGAIN)
          StudioUpgradeReminder(project).doNotAskAgainProjectLevel = true
          StudioUpgradeReminder(project).doNotAskAgainIdeLevel = true
        }
        CANCEL_BUTTON -> logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.CANCEL)
        else -> logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.UNKNOWN_USER_ACTION)
      }
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
    val UPDATE_STUDIO_BUTTON = 0 to "Upgrade Android Studio"
    val DO_NOT_ASK_FOR_PROJECT_BUTTON = 1 to "Don't ask for this project"
    const val CANCEL_BUTTON = -1
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