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

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsPersistentStateComponent
import com.android.tools.idea.insights.ConnectionMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
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
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val DISMISS_OFFLINE_NOTIFICATION_KEY = "noshow"

class ActionToolbarListenerForOfflineBalloon(
  name: String,
  private val project: Project,
  private val offlineAction: AnAction,
  private val scope: CoroutineScope,
  private val offlineStateFlow: Flow<ConnectionMode>
) : ContainerListener {
  private val initialized = AtomicBoolean(false)
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

  private fun showOfflineNotificationBalloon(button: ActionButton) {
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
                AppInsightsPersistentStateComponent.getInstance(project)
                  .isOfflineNotificationDismissed = true
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
          RelativePoint(component, Point(component.preferredSize.width / 2, 0))
      },
      Balloon.Position.above
    )
  }

  override fun componentAdded(e: ContainerEvent) {
    val actionButton = e.child as? ActionButton ?: return
    // Because we don't directly create or manage the buttons on the toolbar,
    // we need to wait for it to be created by the framework first before
    // we can act on it, such as hiding it from view.
    if (actionButton.action == offlineAction && initialized.compareAndSet(false, true)) {
      actionButton.isVisible = false
      if (StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) {
        scope.launch(AndroidDispatchers.uiThread) {
          offlineStateFlow.collect { mode ->
            actionButton.isVisible = mode == ConnectionMode.OFFLINE
            if (
              mode == ConnectionMode.OFFLINE &&
                !AppInsightsPersistentStateComponent.getInstance(project)
                  .isOfflineNotificationDismissed
            ) {
              showOfflineNotificationBalloon(actionButton)
            }
          }
        }
      }
    }
  }
  override fun componentRemoved(e: ContainerEvent?) = Unit
}
