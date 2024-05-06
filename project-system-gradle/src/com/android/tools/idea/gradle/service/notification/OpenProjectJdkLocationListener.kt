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
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import org.jetbrains.annotations.SystemIndependent
import javax.swing.event.HyperlinkEvent

/**
 * A [NotificationListener.Adapter] that allows user to change their project Gradle JDK location from the Project settings popup
 * @param settingsService Android custom settings service for navigation intents
 * @param gradleRootProjectPath Gradle project root absolute path, if specified allows to select the current project
 */
class OpenProjectJdkLocationListener private constructor(
  private val settingsService: AndroidProjectSettingsService,
  private val gradleRootProjectPath: @SystemIndependent String?
): NotificationListener.Adapter() {
  companion object {
    const val ID = "open.project.jdk.location"

    @JvmStatic
    fun create(project: Project, rootProjectPath: @SystemIndependent String?): OpenProjectJdkLocationListener? {
      (ProjectSettingsService.getInstance(project) as? AndroidProjectSettingsService)?.let { service ->
        return OpenProjectJdkLocationListener(service, rootProjectPath)
      }
      return null
    }
  }

  override fun hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
    settingsService.chooseJdkLocation(gradleRootProjectPath)
  }
}