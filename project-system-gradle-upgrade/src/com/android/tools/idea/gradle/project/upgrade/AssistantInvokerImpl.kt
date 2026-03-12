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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType

class AssistantInvokerImpl : AssistantInvoker {

  override fun maybeForceOrRecommendPluginUpgrade(project: Project, info: AndroidPluginInfo) {
    info.pluginVersion?.let { currentAgpVersion ->
      val latestKnown = AgpVersions.latestKnown
      executeOnPooledThread {
        val published = IdeGoogleMavenRepository.getAgpVersions()
        if (shouldForcePluginUpgrade(project, currentAgpVersion, latestKnown, published)) {
          performForcedPluginUpgrade(project, currentAgpVersion)
        } else {
          val recommendation = shouldRecommendPluginUpgrade(project, currentAgpVersion, latestKnown, published)
          if (recommendation.upgrade) recommendPluginUpgrade(project, currentAgpVersion, recommendation.strongly)
        }
      }
    }
  }

  override fun expireProjectUpgradeNotifications(project: Project) {
    NotificationsManager.getNotificationsManager().getNotificationsOfType(ProjectUpgradeNotification::class.java, project).forEach {
      it.expire(false)
    }
  }

  override fun displayForceUpdatesDisabledMessage(project: Project) {
    val msg =
      "Forced upgrades are disabled, errors seen may be due to incompatibilities between " +
        "the Android Gradle Plugin and the version of Android Studio.\nTo re-enable forced updates " +
        "please go to 'Tools > Internal Actions > Edit Studio Flags' and set " +
        "'${StudioFlags.DISABLE_FORCED_UPGRADES.displayName}' to 'Off'."
    val notification =
      NotificationGroupManager.getInstance()
        .getNotificationGroup(AGP_UPGRADE_NOTIFICATION_GROUP_ID)
        ?.createNotification(msg, MessageType.WARNING)
    notification?.notify(project)
  }

  override fun performRecommendedPluginUpgrade(project: Project) {
    executeOnPooledThread { com.android.tools.idea.gradle.project.upgrade.performRecommendedPluginUpgrade(project) }
  }

  @Slow
  override fun shouldRecommendPluginUpgradeToLatest(project: Project): Boolean {
    return shouldRecommendPluginUpgrade(project).upgrade
  }
}
