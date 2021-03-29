/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification

import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import org.jetbrains.plugins.gradle.service.notification.GradleNotificationExtension
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.io.File

class GradleJvmNotificationExtension: GradleNotificationExtension() {
  override fun customize(notificationData: NotificationData, project: Project, error: Throwable?) {
    super.customize(notificationData, project, error)
    if (notificationData.message.startsWith(GradleBundle.message("gradle.jvm.is.invalid"))) {
      val registeredListeners = notificationData.registeredListenerIds
      val gradleProjectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project) ?: return
      if (gradleProjectSettings.gradleJvm != null && gradleProjectSettings.gradleJvm != USE_PROJECT_JDK) {
        if ((registeredListeners != null) && (!registeredListeners.contains(UseJdkAsProjectJdkListener.ID))) {
          val ideSdks = IdeSdks.getInstance()
          val defaultJdk = ideSdks.jdk
          val defaultPath = defaultJdk?.homePath
          if (defaultPath != null) {
            if (ideSdks.validateJdkPath(File(defaultPath)) != null) {
              val listener = UseJdkAsProjectJdkListener(project, defaultPath)
              notificationData.message = notificationData.message + "<a href=\"${UseJdkAsProjectJdkListener.ID}\">" +
                                         "Use JDK ${defaultJdk.name} ($defaultPath)</a>"
              notificationData.setListener(UseJdkAsProjectJdkListener.ID, listener)
            }
          }
        }
      }
      else {
        if ((registeredListeners != null) && (!registeredListeners.contains(OpenProjectJdkLocationListener.ID))) {
          val service = ProjectSettingsService.getInstance(project)
          if (service is AndroidProjectSettingsService) {
            val listener = OpenProjectJdkLocationListener(service)
            notificationData.message = notificationData.message + "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>"
            notificationData.setListener(OpenProjectJdkLocationListener.ID, listener)
          }
        }
      }
    }
  }
}
