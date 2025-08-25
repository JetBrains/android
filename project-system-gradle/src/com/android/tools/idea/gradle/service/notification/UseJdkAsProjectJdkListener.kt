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

import com.android.tools.idea.gradle.project.sync.jdk.GradleJdkConfigurationUtils
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.projectsystem.toReason
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.event.HyperlinkEvent

class UseJdkAsProjectJdkListener(private val project: Project, private val defaultJdkPath: String, idSuffix: String = ""): NotificationListener.Adapter() {
  companion object {
    @VisibleForTesting
    fun baseId() = "use.jdk.as.project.jdk"
  }

  val id = "${baseId()}$idSuffix"

  override fun hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
    val projectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings (project)
    if (projectSettings != null) {
      runWriteAction {
        changeGradleProjectSetting()
      }
      project.getSyncManager().requestSyncProject(GradleSyncStats.Trigger.TRIGGER_QF_GRADLEJVM_TO_USE_PROJECT_JDK.toReason())
    }
    else {
      Messages.showErrorDialog(project, "Could not set project JDK", "Change Gradle JDK")
    }
  }

  @VisibleForTesting
  fun changeGradleProjectSetting() {
    GradleJdkConfigurationUtils.setProjectGradleJdkWithSingleGradleRoot(project, defaultJdkPath)
  }
}
