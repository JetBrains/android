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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.IDevice
import com.android.tools.idea.editors.literals.LiveEditAnActionListener.getLiveEditTriggerShortCutString
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.android.tools.idea.editors.liveedit.ui.ToggleLiveEditStatusAction
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Strings

internal class LiveEditNotifications(val project: Project) {
  // If we haven't let the user know yet that LE is available, do it once per project.
  private var shouldNotifyProjectOfLiveEdit = true

  internal fun notifyLiveEditAvailability(device: IDevice) {
    if (!shouldNotifyProjectOfLiveEdit) {
      return
    }

    shouldNotifyProjectOfLiveEdit = false
    NotificationGroupManager.getInstance().getNotificationGroup("Deploy")
      .createNotification(
        "Enable Live Edit on Device",
        "Push code edits to the device without rerunning the app ${getLiveEditTriggerShortCutString()}.${getBuildSystemRequirements().let { if (Strings.isEmpty(it)) "" else "<br>$it" }}",
        NotificationType.INFORMATION)
      .apply {
        if (LiveEditProjectMonitor.supportLiveEdits(device)) {
          addAction(object : AnAction("Enable Live Edit") {
            override fun actionPerformed(e: AnActionEvent) {
              LiveEditApplicationConfiguration.getInstance().mode = LIVE_EDIT
            }
          })
        }
      }
      .addAction(ToggleLiveEditStatusAction())
      .notify(project)
  }

  // Checks if the build system supports the necessary functionality for desugaring.
  private fun getBuildSystemRequirements(): String {
    if (project.getAndroidFacets().isEmpty()) {
      return ""
    }
    val moduleSystems = project.getAndroidFacets().map { it.getModuleSystem() }
    return if (moduleSystems.all { it.desugarLibraryConfigFilesKnown })
      ""
    else
      moduleSystems.firstNotNullOfOrNull { it.desugarLibraryConfigFilesNotKnownUserMessage } ?: ""
  }
}