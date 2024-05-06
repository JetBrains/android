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

import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.Reservation
import com.android.sdklib.deviceprovisioner.ReservationAction
import com.android.sdklib.deviceprovisioner.ReservationState
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

private const val END_RESERVATION_ID = "android.device.reservation.end"
private val defaultPresentation = DeviceAction.Presentation("", EmptyIcon.ICON_0, true)

class EndReservationActionTest {

  @JvmField @Rule val projectRule = ProjectRule()

  private class FakeDeviceHandle(
    override val scope: CoroutineScope,
    override val stateFlow: StateFlow<DeviceState>,
    override val reservationAction: ReservationAction?,
  ) : DeviceHandle {
    override val id = DeviceId("TEST", false, "")
  }

  @Test
  fun testAction() {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val endReservationAction =
      CustomActionsSchema.getInstance().getCorrectedAction(END_RESERVATION_ID)!!
        as EndReservationAction
    var reservationEnded = false
    val stateFlow =
      MutableStateFlow(
        DeviceState.Disconnected(
          DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE }
        )
      )
    val handle =
      FakeDeviceHandle(
        scope,
        stateFlow,
        object : ReservationAction {
          override suspend fun reserve(duration: Duration): Instant = Instant.now()

          override suspend fun endReservation() {
            reservationEnded = true
          }

          override val presentation = MutableStateFlow(defaultPresentation)
        }
      )
    val dataContext = DataContext { if (it == DEVICE_HANDLE_KEY.name) handle else null }
    val event = AnActionEvent.createFromAnAction(endReservationAction, null, "", dataContext)
    // No reservation available.
    endReservationAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()

    // Reservation with ERROR state.
    stateFlow.update {
      it.copy(reservation = Reservation(ReservationState.ERROR, "", Instant.now(), Instant.now(), null))
    }
    endReservationAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()

    // Reservation with COMPLETE state.
    stateFlow.update {
      it.copy(
        reservation = Reservation(ReservationState.COMPLETE, "", Instant.now(), Instant.now(), null)
      )
    }
    endReservationAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()

    // Active reservation.
    stateFlow.update {
      it.copy(reservation = Reservation(ReservationState.PENDING, "", Instant.now(), Instant.MAX, null))
    }
    endReservationAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.isVisible).isTrue()

    endReservationAction.actionPerformed(event)
    assertThat(reservationEnded).isTrue()
  }
}
