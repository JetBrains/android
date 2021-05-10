/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.UserSentiment
import com.intellij.ide.DataManager
import com.intellij.ide.actions.SendFeedbackAction
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.intellij.xml.util.XmlStringUtil
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

class UserSentimentPanel(private var myProject: Project?,
                         var positive: Boolean) : StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation {

  private var myStatusBar: StatusBar? = null

  private val project: Project?
    get() = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myStatusBar as JComponent?))


  override fun getIcon(): Icon {
    return if (positive) {
      StudioIcons.Shell.Telemetry.SENTIMENT_POSITIVE
    }
    else {
      StudioIcons.Shell.Telemetry.SENTIMENT_NEGATIVE
    }
  }

  override fun ID(): String {
    val pon = if (positive) "Positive" else "Negative"
    return "UserSentimentPanel$pon"
  }

  override fun copy(): StatusBarWidget {
    return UserSentimentPanel(myProject, positive)
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
    return this
  }

  override fun dispose() {
    myProject = null
  }

  override fun install(statusBar: StatusBar) {
    myStatusBar = statusBar
  }

  override fun getTooltipText(): String? {
    return if (positive)
      "Click to let Google know that you are happy with your experience"
    else
      "Click to let Google know that you are unhappy with your experience"
  }

  override fun getClickConsumer(): Consumer<MouseEvent>? {
    return Consumer { _ ->
      logSentiment(if (positive) { UserSentiment.SentimentState.POSITIVE } else { UserSentiment.SentimentState.NEGATIVE })
      showNotification(if (positive) { POSITIVE_SENTIMENT_MESSAGE  } else { NEGATIVE_SENTIMENT_MESSAGE })
    }
  }

  private fun logSentiment(sentiment: UserSentiment.SentimentState) {
    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.USER_SENTIMENT
      userSentiment = UserSentiment.newBuilder().apply {
        state = sentiment
      }.build()
    })
  }

  private fun showNotification(message: String) {
    val listener = NotificationListener { notification, _ ->
      notification.expire()
      if (!positive) {
        logSentiment(UserSentiment.SentimentState.FILE_BUG)
        SendFeedbackAction.submit(project, "Source: user_sentiment_feedback")
      }
    }

    NOTIFICATIONS
      .createNotification(AndroidBundle.message("feedback.notifications.title"), XmlStringUtil.wrapInHtml(message), NotificationType.INFORMATION)
      .setListener(listener)
      .notify(project)
  }

  companion object {
    const val POSITIVE_SENTIMENT_MESSAGE = "We are glad to hear you are having a positive experience with Android Studio!"
    const val NEGATIVE_SENTIMENT_MESSAGE = "We are sorry to hear you are having problems using Android Studio. " +
                                           "Please share <a href='file bug'>detailed feedback</a>."

    @JvmField
    val NOTIFICATIONS = NotificationGroup("Thanks for the feedback!", NotificationDisplayType.BALLOON, true, null, null,
                                          AndroidBundle.message("feedback.notifications.title"))
  }
}
