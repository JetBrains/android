/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.extensions.isProjectUsingDaemonJvmCriteria
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.notification.NotificationListener
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper

class SpawnMultipleDaemonsWarningListener : GradleSyncListenerWithRoot {

  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    if (project.isDisposed) return
    if (!IdeInfo.getInstance().isAndroidStudio) return
    if (!NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId).isShouldLog) return
    if (IdeSdks.getInstance().isUsingJavaHomeJdk(project)) return

    val gradleVersion = GradleSyncStateHolder.getInstance(project).lastSyncedGradleVersion ?: return
    if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(rootProjectPath, gradleVersion)) return

    showMultipleGradleDaemonWarning(project, rootProjectPath)
  }

  @UiThread
  private fun showMultipleGradleDaemonWarning(project: Project, rootProjectPath: @SystemIndependent String) {
    val hyperlinkUrl = AndroidBundle.message("project.sync.warning.multiple.gradle.daemons.url")
    val quickFixes = mutableListOf<NotificationHyperlink>(OpenUrlHyperlink(hyperlinkUrl, "More info..."))
    val selectJdkHyperlink = SelectJdkFromFileSystemHyperlink.create(project, rootProjectPath)
    if (selectJdkHyperlink != null) quickFixes += selectJdkHyperlink
    quickFixes.add(DoNotShowJdkHomeWarningAgainHyperlink())

    var message = AndroidBundle.message(
      "project.sync.warning.multiple.gradle.daemons.message",
      project.name,
      GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath.orEmpty()) ?: "Undefined",
      IdeSdks.getInstance().jdkFromJavaHome ?: "Undefined"
    )
    quickFixes.forEach { quickFix ->
      message += "<br>${quickFix.toHtml()}"
    }
    val listener = NotificationListener { _, event ->
      quickFixes.forEach { link -> link.executeIfClicked(project, event) }
    }

    JDK_LOCATION_WARNING_NOTIFICATION_GROUP.createNotification("", message, MessageType.WARNING.toNotificationType()).apply {
      setListener(listener)
      notify(project)
    }
  }
}