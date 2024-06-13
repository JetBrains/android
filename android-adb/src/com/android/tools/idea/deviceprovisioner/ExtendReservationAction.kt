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
package com.android.tools.idea.deviceprovisioner

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupFactoryImpl
import java.time.Duration
import java.time.Instant
import javax.swing.JComponent
import javax.swing.SwingConstants

object ExtendReservationAction : DefaultActionGroup(), CustomComponentAction {

  init {
    templatePresentation.isPerformGroup = true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.reservationAction() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.inputEvent?.component ?: return
    val popup =
      JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.dataContext, null, true)

    @Suppress("UNCHECKED_CAST")
    val actionList = popup.listStep.values as List<PopupFactoryImpl.ActionItem>
    val anyActionEnabled = actionList.any { it.isEnabled }
    if (!anyActionEnabled) {
      popup.setAdText("Device reserved for the 180 minutes maximum duration", SwingConstants.LEFT)
    } else {
      popup.addListSelectionListener { selectEvent ->
        val text =
          when (
            val selectedItem =
              PlatformCoreDataKeys.SELECTED_ITEM.getData(selectEvent.source as DataProvider)
          ) {
            is PopupFactoryImpl.ActionItem -> selectedItem.description
            else -> ""
          }
        popup.setAdText(text, SwingConstants.LEFT)
      }
    }
    popup.showUnderneathOf(component)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  object Status : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
      val presentation = event.presentation
      presentation.isEnabled = false
      val handle = event.deviceHandle()
      if (handle?.reservationAction == null) {
        presentation.isVisible = false
        return
      }
      val reservation = handle.state.reservation
      val endTime = reservation?.endTime?.toEpochMilli()
      if (endTime == null) {
        presentation.text = "Reservation remaining time not available"
        return
      }
      val timeLeft = Duration.between(Instant.now(), reservation.endTime).toMinutes()
      val timeLeftText = if (timeLeft < 1) "less than 1 min" else "$timeLeft min"
      presentation.text = "Reservation: $timeLeftText remaining"
    }

    override fun actionPerformed(e: AnActionEvent) = Unit
  }

  object Extend15MinOrLessAction : ExtendAction(1, 15)

  object Extend30MinOrLessAction : ExtendAction(16, 30)

  open class ExtendAction(
    private val minimumExtendMinutes: Long,
    private val maximumExtendMinutes: Long,
  ) : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
      event.presentation.isVisible = event.reservationAction() != null
      val possibleExtendMinutes =
        event.getPossibleExtendMinutes().coerceAtMost(maximumExtendMinutes)
      // Action can only be performed if reservation can be extended by at least 1 min.
      event.presentation.isEnabled = possibleExtendMinutes >= minimumExtendMinutes
      val phrase =
        when {
          possibleExtendMinutes < minimumExtendMinutes -> "$maximumExtendMinutes mins"
          possibleExtendMinutes == 1L -> "1 min"
          else -> "$possibleExtendMinutes mins"
        }
      event.presentation.text = "Extend $phrase"
      event.presentation.description = "Extend the device reservation by $phrase"
    }

    override fun actionPerformed(e: AnActionEvent) {
      val handle = e.deviceHandle() ?: return
      val possibleExtendMinutes = e.getPossibleExtendMinutes().coerceAtMost(maximumExtendMinutes)
      handle.launchCatchingDeviceActionException {
        handle.reservationAction?.reserve(Duration.ofMinutes(possibleExtendMinutes))
      }
    }
  }
}

private fun AnActionEvent.maxReservationDuration() = deviceHandle()?.state?.reservation?.maxDuration

/** Get remaining time left between current end time and maximum possible end time. */
private fun AnActionEvent.getPossibleExtendMinutes(): Long {
  val handle = deviceHandle() ?: return 0

  val maxDuration = maxReservationDuration() ?: return 0
  val startTime = handle.state.reservation?.startTime ?: return 0
  val endTime = handle.state.reservation?.endTime ?: return 0

  return Duration.between(endTime, startTime.plus(maxDuration)).toMinutes()
}
