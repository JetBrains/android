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

import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.sdk.IdeSdks
import com.android.utils.FileUtils
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
      // Suggest use embedded
      var registeredListeners = notificationData.registeredListenerIds
      val ideSdks = IdeSdks.getInstance()
      val embeddedJdkPath = ideSdks.embeddedJdkPath
      if (embeddedJdkPath != null) {
        if (ideSdks.validateJdkPath(embeddedJdkPath) != null) {
          val absolutePath = embeddedJdkPath.absolutePath
          val listener = UseJdkAsProjectJdkListener(project, absolutePath, ".embedded")
          if (!registeredListeners.contains(listener.id)) {
            notificationData.message = notificationData.message + "<a href=\"${listener.id}\">Use Embedded JDK ($absolutePath)</a>\n"
            notificationData.setListener(listener.id, listener)
            registeredListeners = notificationData.registeredListenerIds
          }
        }
      }
      // Suggest IdeSdks.jdk (if different to embedded)
      val defaultJdk = ideSdks.jdk
      val defaultPath = defaultJdk?.homePath
      if (defaultPath != null) {
        if (ideSdks.validateJdkPath(File(defaultPath)) != null) {
          if (embeddedJdkPath == null || (!FileUtils.isSameFile(embeddedJdkPath, File(defaultPath)))) {
            val listener = UseJdkAsProjectJdkListener(project, defaultPath)
            if (!registeredListeners.contains(listener.id)) {
              notificationData.message = notificationData.message + "<a href=\"${listener.id}\">Use JDK ${defaultJdk.name} ($defaultPath)</a>\n"
              notificationData.setListener(listener.id, listener)
              registeredListeners = notificationData.registeredListenerIds
            }
          }
        }
      }
      // Add change JDK location link
      if ((registeredListeners != null) && (!registeredListeners.contains(OpenProjectJdkLocationListener.ID))) {
        val service = ProjectSettingsService.getInstance(project)
        if (service is AndroidProjectSettingsService) {
          val listener = OpenProjectJdkLocationListener(service)
          notificationData.message = notificationData.message + "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>\n"
          notificationData.setListener(OpenProjectJdkLocationListener.ID, listener)
        }
      }
    }
  }
}
