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

import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.jdk.GradleJdkValidationManager
import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * Project startup activity run ONLY in Android Studio. It should not be registered as part of the Android IntelliJ plugin.
 *
 * Note: project import is triggered by a different startup activity (maybe) run in parallel and as such anything that requires Android
 * models to be present should not be called here directly but instead by the appropriate listeners or callbacks.
 */
class AndroidStudioProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.cleanUpPreferences(project)

    // Custom notifications for Android Studio, un-wanted or un-needed when running as the Android IntelliJ plugin
    notifyOnLegacyAndroidProject(project)
    notifyOnInvalidGradleJDKEnv(project)
    // Many JDK tests rely on this not running in order to test invalid JDK messages.
    // This is not an ideal solution but allows it to work for now.
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      checkForInvalidGradleJdksAndAttemptToRecover(project)
    }
  }
}

private fun notifyOnLegacyAndroidProject(project: Project) {
  val legacyAndroidProjects = LegacyAndroidProjects(project)
  if (AndroidProjectInfo.getInstance(project).isLegacyIdeaAndroidProject
      && !AndroidProjectInfo.getInstance(project).isApkProject) {
    legacyAndroidProjects.trackProject()
    if (!Info.getInstance(project).isBuildWithGradle) {
      // Suggest that Android Studio users use Gradle instead of IDEA project builder.
      legacyAndroidProjects.showMigrateToGradleWarning()
    }
  }
}

private fun notifyOnInvalidGradleJDKEnv(project: Project) {
  val ideSdks = IdeSdks.getInstance()
  if (ideSdks.isJdkEnvVariableDefined && !ideSdks.isJdkEnvVariableValid) {
    val msg = IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME +
              " is being ignored since it is set to an invalid JDK Location:\n" +
              ideSdks.envVariableJdkValue
    val hyperlinks = listOfNotNull(SelectJdkFromFileSystemHyperlink.create(project, project.basePath))
    AndroidNotification.getInstance(project).showBalloon(
      "", msg, NotificationType.WARNING, *hyperlinks.toTypedArray()
    )
  }
}

private suspend fun checkForInvalidGradleJdksAndAttemptToRecover(project: Project) {
  GradleSettings.getInstance(project).linkedProjectsSettings
    .mapNotNull { it.externalProjectPath }
    .plus(project.basePath)
    .filterNotNull()
    .toSet()
    .forEach {
      val gradleJdkException = GradleJdkValidationManager.getInstance(project).validateProjectGradleJvmPath(project, it)
      withContext(Dispatchers.EDT) {
        runWriteAction {
          gradleJdkException?.recover()
        }
      }
    }
}