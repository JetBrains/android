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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.PROJECT_SYSTEM
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_CTA_OLD_AGP_DISMISSED
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent

val AGP_UPGRADE_NOTIFICATION_GROUP = NotificationGroup("Android Gradle Upgrade Notification", NotificationDisplayType.STICKY_BALLOON, true)

private val LOG = Logger.getInstance(LOG_CATEGORY)

sealed class ProjectUpgradeNotification(title: String, content: String, type: NotificationType)
  : Notification(AGP_UPGRADE_NOTIFICATION_GROUP.displayId, title, content, type) {
  var callToActionDismissed = false
  var studioEventSent = false
  abstract val project: Project
  abstract val currentAgpVersion: AgpVersion

  init {
    this.addAction(object : AnAction("Start AGP Upgrade Assistant") {
      override fun actionPerformed(e: AnActionEvent) {
        this@ProjectUpgradeNotification.expire(false)
        LOG.info("Starting AGP Upgrade Assistant")
        e.project?.let { performRecommendedPluginUpgrade(it) }
      }
    })
    this.addAction(object : AnAction("Remind me tomorrow") {
      override fun actionPerformed(e: AnActionEvent) {
        this@ProjectUpgradeNotification.expire(false)
        LOG.info("AGP Upgrade notification postponed for 24 hours")
        e.project?.let { RecommendedUpgradeReminder(it).updateLastTimestamp() }
      }
    })
    this.addAction(object : AnAction("Don't ask for this project") {
      override fun actionPerformed(e: AnActionEvent) {
        this@ProjectUpgradeNotification.expire(true)
        LOG.info("AGP Upgrade notification disabled for this project")
        e.project?.let { RecommendedUpgradeReminder(it).doNotAskForProject = true }
      }
    })
    this.addAction(object : AnAction("Don't show again") {
      override fun actionPerformed(e: AnActionEvent) {
        this@ProjectUpgradeNotification.expire(true)
        LOG.info("AGP Upgrade notification disabled application-wide")
        e.project?.let { RecommendedUpgradeReminder(it).doNotAskForApplication = true }
      }
    })
  }

  override fun setBalloon(balloon: Balloon) {
    super.setBalloon(balloon)
    // This listener catches closing of the balloon, both explicitly (from clicking the close button) and implicitly (e.g. when expiring).
    balloon.addListener(object: JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        // If we have been implicitly closed (by running sync, or from changes to Gradle files), then the call to expire(Boolean)
        // will have sent the event if appropriate.  This call handles cases where the balloon is being closed more directly.
        //
        // (In practice, this will not count as a "dismissal"; we identify dismissals as being the actions that will prevent the
        // notification from reoccurring completely: on this project or in the application.  Leaving this here in case that changes.
        maybeFireOldAgpCtaDismissedEvent()
      }
    })
  }

  override fun expire() {
    if (!isExpired) {
      maybeFireOldAgpCtaDismissedEvent()
    }
    super.expire()
  }

  fun expire(dismissed: Boolean) {
    callToActionDismissed = dismissed
    expire()
  }

  private fun maybeFireOldAgpCtaDismissedEvent() {
    if (callToActionDismissed && currentAgpVersion < OLD_AGP_VERSION && !studioEventSent) {
      val studioEvent = AndroidStudioEvent.newBuilder()
        .setCategory(PROJECT_SYSTEM)
        .setKind(UPGRADE_ASSISTANT_CTA_OLD_AGP_DISMISSED)
        .withProjectId(project)
      UsageTracker.log(studioEvent)
      studioEventSent = true
    }
  }

  companion object {
    val OLD_AGP_VERSION = AgpVersion.parse("7.0.0")
  }
}

class UpgradeSuggestion(title: String, content: String, override val project: Project, override val currentAgpVersion: AgpVersion)
  : ProjectUpgradeNotification(title, content, NotificationType.INFORMATION) {
    init {
      isSuggestionType = true
    }
  }

class DeprecatedAgpUpgradeWarning(title: String, content: String, override val project: Project, override val currentAgpVersion: AgpVersion)
  : ProjectUpgradeNotification(title, content, NotificationType.WARNING) {
    init {
      isSuggestionType = true
      isImportantSuggestion = true
    }
  }
