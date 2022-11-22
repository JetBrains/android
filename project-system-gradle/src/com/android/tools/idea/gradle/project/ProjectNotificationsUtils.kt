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
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.gradle.service.notification.GradleJvmNotificationExtension.Companion.getInvalidJdkReason
import com.android.tools.idea.gradle.service.notification.GradleJvmNotificationExtension.Companion.reportInvalidJdkReasonToUsageTracker
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting

fun showNeededNotifications(project: Project) {
  if (IdeInfo.getInstance().isAndroidStudio) {
    notifyOnLegacyAndroidProject(project)
    notifyOnInvalidGradleJDKEnv(project)
    if (notifyOnInvalidGradleJdk(project)) {
      runWriteAction { JdkUtils.setProjectGradleJvmToUseEmbeddedJdk(project, project.basePath.orEmpty()) }
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

@VisibleForTesting
fun notifyOnInvalidGradleJdk(project: Project): Boolean {
  val jdkInvalidReason = getInvalidJdkReason(project)
  if (jdkInvalidReason != null) {
    val ideSdks = IdeSdks.getInstance()
    val embeddedJdkPath = ideSdks.embeddedJdkPath
    val errorResolution: String
    val notificationType: NotificationType
    val shouldUseEmbedded: Boolean
    if (embeddedJdkPath != null && (ideSdks.validateJdkPath(embeddedJdkPath) != null)) {
      // Can use embedded JDK as alternative, do so and warn user of change
      errorResolution = "Gradle JVM setting was changed to use Embedded JDK."
      notificationType = NotificationType.WARNING
      shouldUseEmbedded = true
    }
    else {
      // Cannot use embedded, notify as error
      errorResolution = "Having an incorrect Gradle JDK may result in unresolved symbols and problems when running Gradle tasks."
      notificationType = NotificationType.ERROR
      shouldUseEmbedded = false
    }
    showBalloon(project, jdkInvalidReason.message, errorResolution, notificationType)
    reportInvalidJdkReasonToUsageTracker(project, jdkInvalidReason.reason)
    return shouldUseEmbedded
  }
  return false
}

private fun showBalloon(project: Project, errorReason: String, errorText: String, notificationType: NotificationType) {
  val quickFixes = generateInvalidGradleJdkLinks(project)
  AndroidNotification.getInstance(project).showBalloon(errorReason,errorText, notificationType, *quickFixes.toTypedArray())
}

@VisibleForTesting
fun generateInvalidGradleJdkLinks(project: Project): ArrayList<NotificationHyperlink> {
  val quickFixes: ArrayList<NotificationHyperlink> = arrayListOf()

  val selectJdkLink = SelectJdkFromFileSystemHyperlink.create(project)
  if (selectJdkLink != null) {
    quickFixes.add(selectJdkLink)
  }
  return quickFixes
}
