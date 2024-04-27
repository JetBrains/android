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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.memorysettings.MemorySettingsUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorNotification
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.delay
import org.jetbrains.android.util.AndroidBundle


class EssentialsModeRecommender : ProjectActivity {
  val ignoreEssentialsMode = "ignore.essentials.mode.recommendation"
  val notificationGroup = "Essentials Mode"

  fun shouldRecommend(): Boolean {
    if (!StudioFlags.ESSENTIALS_MODE_VISIBLE.get()) return false
    if (!StudioFlags.ESSENTIALS_MODE_GETS_RECOMMENDED.get()) return false
    if (PropertiesComponent.getInstance().getBoolean(ignoreEssentialsMode)) return false
    return !EssentialsMode.isEnabled()
  }

  fun recommendEssentialsMode() {
    if (!shouldRecommend()) return
    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(notificationGroup) ?: return


    val notification = notificationGroup.createNotification(
      AndroidBundle.message("essentials.mode.recommendation.title"),
      AndroidBundle.message("essentials.mode.recommendation.content"),
      com.intellij.notification.NotificationType.INFORMATION
    )
    notification.addAction(EssentialsModeResponseYes())
    notification.addAction(EssentialsModeResponseNotNow())
    notification.addAction(EssentialsModeResponseDoNotShowAgain())

    ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }

    UsageTracker.log(AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.EDITOR_NOTIFICATION).setCategory(
      AndroidStudioEvent.EventCategory.STUDIO_UI)
                       .setEditorNotification(
                         EditorNotification.newBuilder().setNotificationType(EditorNotification.NotificationType.ESSENTIALS_MODE)))
  }

  // Creating separate inner classes, so we are able to tell which response a user selects in our tracking via class names
  inner class EssentialsModeResponseYes : NotificationAction("Yes") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      EssentialsMode.setEnabled(true, e.project)
      notification.expire()
    }
  }

  inner class EssentialsModeResponseDoNotShowAgain : NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      PropertiesComponent.getInstance().setValue(ignoreEssentialsMode, true)
      notification.expire()
    }
  }

  inner class EssentialsModeResponseNotNow : NotificationAction("Not right now") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      notification.expire()
    }
  }

  override suspend fun execute(project: Project) {
    // recommend to users with <= 8 GB of RAM 5 minutes after startup
    val memoryInGB = MemorySettingsUtil.getMachineMem() shr 10
    if (IdeInfo.getInstance().isAndroidStudio && memoryInGB <= 8 && !ApplicationManager.getApplication().isUnitTestMode) {
      delay(1000L * 60 * 5)
      recommendEssentialsMode()
    }
  }
}