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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.PositionTracker
import java.awt.Point
import javax.swing.event.HyperlinkEvent

private const val DISMISS_OFFLINE_NOTIFICATION_KEY = "noshow"

class OfflineBalloonMaker(name: String, private val project: Project) {
  private val offlineErrorContent =
    "<b>Offline</b><br/>" +
      "<br/>You are currently in offline mode because<br/>" +
      "Android Studio could not reach $name.<br/><br/>" +
      "You can see issues already fetched from the<br/>" +
      "last time you were online and your actions<br/>" +
      "are saved locally. You can click Reconnect to<br/>" +
      "exit offline mode. Reconnect will fetch<br/>" +
      "new content and send your changes.<br/><br/>" +
      "<a href='dismiss'>Dismiss</a>&nbsp;&nbsp;&nbsp;&nbsp;<a href='${DISMISS_OFFLINE_NOTIFICATION_KEY}'>Don't show again</a>"

  fun showOfflineNotificationBalloon(button: ActionButton) {
    val background = MessageType.ERROR.popupBackground
    lateinit var balloon: Balloon
    balloon =
      JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(
          offlineErrorContent,
          offlineModeIcon,
          background,
          object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
              balloon.hide()
              if (e.description == DISMISS_OFFLINE_NOTIFICATION_KEY) {
                project.service<AppInsightsSettings>().isOfflineNotificationDismissed = true
              }
            }
          }
        )
        .setBorderColor(JBColor.border())
        .setBorderInsets(JBInsets.create(4, 4))
        .createBalloon()
    balloon.show(
      object : PositionTracker<Balloon>(button) {
        override fun recalculateLocation(balloon: Balloon) =
          RelativePoint(component, Point(component.preferredSize.width / 4, 0))
      },
      Balloon.Position.above
    )
  }
}
