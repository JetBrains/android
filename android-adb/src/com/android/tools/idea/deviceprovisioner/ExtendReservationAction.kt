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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class ExtendReservationAction : DefaultActionGroup() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.reservationAction() != null
  }

  init {
    // Add a disabled action with remaining time.
    add(
      object : AnAction() {
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
            presentation.text = "Reservation remaining time not available."
            return
          }
          val timeAccuracy = TimeUnit.MINUTES.toMillis(1)
          val timeLeft =
            StringUtil.formatDuration((endTime - Instant.now().toEpochMilli()) / timeAccuracy * timeAccuracy)
          presentation.text = "Reservation: $timeLeft remaining."
        }

        override fun actionPerformed(e: AnActionEvent) = Unit
      },
      Constraints.FIRST
    )
  }

  class HalfHour : ExtendFixedDurationAction(Duration.ofMinutes(30))
  class OneHour : ExtendFixedDurationAction(Duration.ofHours(1))

  open class ExtendFixedDurationAction(private val duration: Duration) : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
      event.presentation.isEnabledAndVisible = event.reservationAction() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      val handle = e.deviceHandle() ?: return
      handle.scope.launch { handle.reservationAction?.reserve(duration) }
    }
  }
}

private fun AnActionEvent.deviceHandle() = DEVICE_HANDLE_KEY.getData(dataContext)

private fun AnActionEvent.reservationAction() = deviceHandle()?.reservationAction
