/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

val AGP_UPGRADE_NOTIFICATION_GROUP = NotificationGroup("Android Gradle Upgrade Notification", NotificationDisplayType.STICKY_BALLOON, true)

abstract class ProjectUpgradeNotification(title: String, content: String, type: NotificationType)
  : Notification(AGP_UPGRADE_NOTIFICATION_GROUP.displayId, title, content, type) {
    init {
      addAction(object : AnAction("Start AGP Upgrade Assistant") {
        override fun actionPerformed(e: AnActionEvent) {
          this@ProjectUpgradeNotification.expire()
          e.project?.let { performRecommendedPluginUpgrade(it) }
        }
      })
      addAction(object : AnAction("Remind me tomorrow") {
        override fun actionPerformed(e: AnActionEvent) {
          this@ProjectUpgradeNotification.expire()
          e.project?.let { RecommendedUpgradeReminder(it).updateLastTimestamp() }
        }
      })
      addAction(object : AnAction("Don't ask for this project") {
        override fun actionPerformed(e: AnActionEvent) {
          this@ProjectUpgradeNotification.expire()
          e.project?.let { RecommendedUpgradeReminder(it).doNotAskForProject = true }
        }
      })
      addAction(object : AnAction("Don't show again") {
        override fun actionPerformed(e: AnActionEvent) {
          this@ProjectUpgradeNotification.expire()
          e.project?.let { RecommendedUpgradeReminder(it).doNotAskForApplication = true }
        }
      })
    }
  }

class UpgradeSuggestion(title: String, content: String)
  : ProjectUpgradeNotification(title, content, NotificationType.INFORMATION)

class DeprecatedAgpUpgradeWarning(title: String, content: String)
  : ProjectUpgradeNotification(title, content, NotificationType.WARNING)
