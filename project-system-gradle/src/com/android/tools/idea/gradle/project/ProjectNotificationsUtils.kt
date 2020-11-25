/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.UseEmbeddedJdkHyperlink
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import java.nio.file.Paths

fun showNeededNotifications(project: Project) {
  if (IdeInfo.getInstance().isAndroidStudio) {
    notifyOnLegacyAndroidProject(project)
    notifyOnInvalidGradleJDKEnv(project)
    val projectJdkPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath!!)
    val jdkErrorMessage: String? = invalidJdkErrorMessage(projectJdkPath)
    if (jdkErrorMessage != null) {
      notifyOnInvalidGradleJdk(project, jdkErrorMessage)
    }
  }
}

private fun notifyOnLegacyAndroidProject(project: Project) {
  val legacyAndroidProjects = LegacyAndroidProjects(project)
  if (AndroidProjectInfo.getInstance(project).isLegacyIdeaAndroidProject
      && !AndroidProjectInfo.getInstance(project).isApkProject) {
    legacyAndroidProjects.trackProject()
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle) {
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
    AndroidNotification.getInstance(project).showBalloon("", msg, NotificationType.WARNING,
                                                         SelectJdkFromFileSystemHyperlink.create(project)!!)
  }
}

private fun notifyOnInvalidGradleJdk(project: Project, errorMessage: String) {
  val quickFixes = generateInvalidGradleJdkLinks(project)
  AndroidNotification.getInstance(project).showBalloon("", errorMessage, NotificationType.ERROR, *quickFixes.toTypedArray())
}

@VisibleForTesting
fun invalidJdkErrorMessage(jdkPath: String?): String? {
  var errorMessage: String? = null
  if (jdkPath == null) {
    errorMessage = "Could not determine Gradle JDK"
  }
  else {
    val ideSdks = IdeSdks.getInstance()
    if (ideSdks.validateJdkPath(Paths.get(jdkPath)) == null) {
      errorMessage = "Could not find a valid JDK at $jdkPath"
    }
  }
  if (errorMessage != null) {
    errorMessage = "$errorMessage\nHaving an incorrect Gradle JDK may result in unresolved symbols and problems when running Gradle tasks."
  }
  return errorMessage
}

@VisibleForTesting
fun generateInvalidGradleJdkLinks(project: Project): ArrayList<NotificationHyperlink> {
  val quickFixes: ArrayList<NotificationHyperlink> = arrayListOf()

  val ideSdks = IdeSdks.getInstance()
  val embeddedJdkPath = ideSdks.embeddedJdkPath
  if (embeddedJdkPath != null && (ideSdks.validateJdkPath(embeddedJdkPath) != null)) {
    quickFixes.add(UseEmbeddedJdkHyperlink())
  }
  val selectJdkLink = SelectJdkFromFileSystemHyperlink.create(project)
  if (selectJdkLink != null) {
    quickFixes.add(selectJdkLink)
  }
  return quickFixes
}
