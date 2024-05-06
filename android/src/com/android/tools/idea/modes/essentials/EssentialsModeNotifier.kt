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
package com.android.tools.idea.modes.essentials

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle

@Service(Service.Level.PROJECT)
class EssentialsModeNotifier {

  private var notification = EssentialsModeNotification()
  val ignore : String = AndroidBundle.message("essentials.mode.notification.ignore")


  fun notifyProject() {
    if (PropertiesComponent.getInstance().getBoolean(ignore)) {
      return
    }
    if (EssentialsMode.isEnabled()) {
      notification = EssentialsModeNotification()
      notification.notify(null)
    } else {
      notification.expire()
    }
  }

  inner class EssentialsModeNotification : Notification(AndroidBundle.message("essentials.mode.group"),
                                                           AndroidBundle.message("essentials.mode.notification.title"),
                                                           AndroidBundle.message("essentials.mode.notification.content"),
                                                           NotificationType.WARNING) {
    init {
      addAction(object : NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          PropertiesComponent.getInstance().setValue(ignore, true)
          notification.expire()
        }
      })
      addAction(object : NotificationAction("Disable Essentials mode") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          EssentialsMode.setEnabled(false, e.project)
          notification.expire()
        }
      })
    }
  }
}
