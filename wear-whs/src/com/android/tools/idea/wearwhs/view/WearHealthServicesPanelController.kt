/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val NOTIFICATION_GROUP_ID = "Wear Health Services Notification"
private val TEMPORARY_MESSAGE_DISPLAY_DURATION = 2.seconds

internal class WearHealthServicesPanelController(
  private val stateManager: WearHealthServicesStateManager,
  private val workerScope: CoroutineScope,
  private val uiScope: CoroutineScope,
) {

  private var currentBalloon: Balloon? = null
  private val userInformationFlow =
    MutableStateFlow<PanelInformation>(PanelInformation.EmptyMessage)

  init {
    workerScope.launch {
      stateManager.status.collect {
        if (it is WhsStateManagerStatus.Syncing) {
          userInformationFlow.value =
            PanelInformation.Message(message("wear.whs.panel.capabilities.syncing"))
        }
      }
    }

    workerScope.launch {
      userInformationFlow.collectLatest {
        if (it is PanelInformation.TemporaryMessage) {
          delay(it.duration)
          userInformationFlow.value = PanelInformation.EmptyMessage
        }
      }
    }
  }

  fun showWearHealthServicesToolPopup(parentDisposable: Disposable, position: RelativePoint) {
    val panelUiScope = parentDisposable.createCoroutineScope(uiThread)
    val panelWorkerScope = parentDisposable.createCoroutineScope(workerThread)
    val panel =
      createWearHealthServicesPanel(
        stateManager,
        uiScope = panelUiScope,
        workerScope = panelWorkerScope,
        informationLabelFlow = userInformationFlow.map { it.message },
        reset = ::reset,
        applyChanges = ::applyChanges,
        triggerEvent = ::triggerEvent,
      )

    val balloon =
      JBPopupFactory.getInstance()
        .createBalloonBuilder(panel.component)
        .setShadow(true)
        .setHideOnAction(false)
        .setBlockClicksThroughBalloon(true)
        .setRequestFocus(true)
        .setAnimationCycle(200)
        .setFillColor(secondaryPanelBackground)
        .setBorderColor(secondaryPanelBackground)
        .createBalloon()

    // Hide the balloon if Studio looses focus:
    val window = SwingUtilities.windowForComponent(position.component)
    if (window != null) {
      val listener =
        object : WindowAdapter() {
          override fun windowLostFocus(event: WindowEvent) {
            balloon.hide()
          }
        }
      window.addWindowFocusListener(listener)
      Disposer.register(balloon) { window.removeWindowFocusListener(listener) }
    }

    // Hide the balloon when the parentDisposable is disposed
    Disposer.register(parentDisposable, balloon)
    Disposer.register(balloon) {
      panelUiScope.cancel()
      panelWorkerScope.cancel()
      currentBalloon = null
    }

    currentBalloon = balloon

    // Show the balloon above the component if there is room, otherwise below:
    balloon.show(position, Balloon.Position.above)
  }

  private fun notifyUserInPanelIfOpen(message: String, type: MessageType) {
    uiScope.launch {
      val isBalloonShowing = currentBalloon != null
      if (isBalloonShowing) {
        userInformationFlow.value = PanelInformation.TemporaryMessage(message)
      } else {
        notifyWithBalloon(message, type)
      }
    }
  }

  private fun notifyWithBalloon(message: String, type: MessageType) {
    userInformationFlow.value = PanelInformation.EmptyMessage
    Notifications.Bus.notify(
      Notification(NOTIFICATION_GROUP_ID, message, type.toNotificationType())
    )
  }

  private fun reset() {
    workerScope.launch {
      stateManager
        .reset()
        .onSuccess {
          notifyUserInPanelIfOpen(message("wear.whs.panel.reset.success"), MessageType.INFO)
        }
        .onFailure {
          notifyUserInPanelIfOpen(message("wear.whs.panel.reset.failure"), MessageType.ERROR)
        }
    }
  }

  private fun applyChanges() {
    currentBalloon?.hide()
    workerScope.launch {
      val applyType = if (stateManager.hasUserChanges.value) "apply" else "reapply"
      val changesApplied =
        if (stateManager.ongoingExercise.value) "sensor.values" else "capabilities"

      stateManager
        .applyChanges()
        .onSuccess {
          notifyWithBalloon(
            message("wear.whs.panel.$applyType.$changesApplied.success"),
            MessageType.INFO,
          )
        }
        .onFailure {
          notifyWithBalloon(
            message("wear.whs.panel.$applyType.$changesApplied.failure"),
            MessageType.ERROR,
          )
        }
    }
  }

  private fun triggerEvent(eventTrigger: EventTrigger) {
    currentBalloon?.hide()
    workerScope.launch {
      stateManager
        .triggerEvent(eventTrigger)
        .onSuccess {
          notifyWithBalloon(message("wear.whs.event.trigger.success"), MessageType.INFO)
        }
        .onFailure {
          notifyWithBalloon(message("wear.whs.event.trigger.failure"), MessageType.ERROR)
        }
    }
  }
}

private sealed class PanelInformation(val message: String) {
  class Message(message: String) : PanelInformation(message)

  class TemporaryMessage(
    message: String,
    val duration: Duration = TEMPORARY_MESSAGE_DISPLAY_DURATION,
  ) : PanelInformation(message)

  data object EmptyMessage : PanelInformation("")
}
