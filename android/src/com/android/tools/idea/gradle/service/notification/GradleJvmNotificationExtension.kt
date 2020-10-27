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

import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.notification.GradleNotificationExtension
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleJvmNotificationExtension: GradleNotificationExtension() {
  override fun customize(notificationData: NotificationData, project: Project, error: Throwable?) {
    super.customize(notificationData, project, error)
    if (notificationData.message.startsWith(GradleBundle.message("gradle.jvm.is.invalid"))) {
      val registeredListeners = notificationData.registeredListenerIds
      if ((registeredListeners != null) && (!registeredListeners.contains(UseProjectJdkAsGradleJvmListener.ID))) {
        val listener = UseProjectJdkAsGradleJvmListener(project)
        notificationData.message = notificationData.message + "<a href=\"${UseProjectJdkAsGradleJvmListener.ID}\">Use JDK from project structure</a>"
        notificationData.setListener(UseProjectJdkAsGradleJvmListener.ID, listener)
      }
    }
  }
}
