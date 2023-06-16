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
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

private const val EXTEND_RESERVATION_ID = "android.device.reservation.extend"
private const val EXTEND_RESERVATION_HALF_HOUR_ID = "android.device.reservation.extend.half.hour"
private const val EXTEND_RESERVATION_ONE_HOUR_ID = "android.device.reservation.extend.one.hour"
private val defaultPresentation = DeviceAction.Presentation("", EmptyIcon.ICON_0, true)

class ExtendReservationActionTest {

  @JvmField
  @Rule
  val projectRule = ProjectRule()

  private class FakeDeviceHandle(
    override val scope: CoroutineScope,
    override val stateFlow: StateFlow<DeviceState>,
    override val reservationAction: ReservationAction?,
  ) : DeviceHandle

  @Test
  fun testHandlesWithReservationActions() {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val extendReservationAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ID)!! as ExtendReservationAction
    assertThat(extendReservationAction.childrenCount).isEqualTo(3)
    var totalDuration = Duration.ZERO
    val handle = FakeDeviceHandle(
      scope,
      MutableStateFlow(DeviceState.Disconnected(DeviceProperties.Builder().apply {
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
      }.buildBase())),
      object : ReservationAction {
        override suspend fun reserve(duration: Duration): Instant {
          totalDuration = totalDuration.plus(duration)
          return Instant.ofEpochSecond(duration.toMillis())
        }

        override suspend fun endReservation() = Unit

        override val presentation = MutableStateFlow(defaultPresentation)
      })
    val dataContext = DataContext {
      if (it == DEVICE_HANDLE_KEY.name) handle
      else null
    }
    val event = AnActionEvent.createFromAnAction(extendReservationAction, null, "", dataContext)
    val actions = extendReservationAction.getChildren(event)
    val remainingTimeAction = AnActionEvent.createFromAnAction(actions[0], null, "", dataContext)
    actions[0].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation remaining time not available")
    val extendAction = AnActionEvent.createFromAnAction(actions[1], null, "", dataContext)

    // Verify that actions from extendReservationAction's children are equivalent with actions from CustomActionsSchema.
    val extendHalfHourAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
      as ExtendReservationAction.HalfHour
    val extendOneHourAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ONE_HOUR_ID)
      as ExtendReservationAction.OneHour
    assertThat(extendHalfHourAction).isEqualTo(actions[1])
    assertThat(extendOneHourAction).isEqualTo(actions[2])

    assertThat(totalDuration).isEqualTo(Duration.ZERO)
    // Extend 30 minutes.
    val updateAction1 = AnActionEvent.createFromAnAction(actions[1], null, "", dataContext)
    extendHalfHourAction.update(updateAction1)
    assertThat(updateAction1.presentation.isEnabledAndVisible).isTrue()
    extendHalfHourAction.actionPerformed(extendAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(30))
    // Extend 1 hour.
    val updateAction2 = AnActionEvent.createFromAnAction(extendOneHourAction, null, "", dataContext)
    extendOneHourAction.update(updateAction1)
    assertThat(updateAction2.presentation.isEnabledAndVisible).isTrue()
    extendOneHourAction.actionPerformed(extendAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(90))
  }

  @Test
  fun testExtendFixedDurationActionsIndependently() {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    var totalDuration = Duration.ZERO
    val handle = FakeDeviceHandle(
      scope,
      MutableStateFlow(DeviceState.Disconnected(DeviceProperties.Builder().apply {
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
      }.buildBase())),
      object : ReservationAction {
        override suspend fun reserve(duration: Duration): Instant {
          totalDuration = totalDuration.plus(duration)
          return Instant.ofEpochSecond(duration.toMillis())
        }

        override suspend fun endReservation() = Unit

        override val presentation = MutableStateFlow(defaultPresentation)
      })
    val dataContext = DataContext {
      if (it == DEVICE_HANDLE_KEY.name) handle
      else null
    }
    val extendHalfHourAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
      as ExtendReservationAction.HalfHour
    val extendOneHourAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ONE_HOUR_ID)
      as ExtendReservationAction.OneHour
    assertThat(totalDuration).isEqualTo(Duration.ZERO)
    // Extend 30 minutes.
    val updateAction1 = AnActionEvent.createFromAnAction(extendHalfHourAction, null, "", dataContext)
    extendHalfHourAction.update(updateAction1)
    assertThat(updateAction1.presentation.isEnabledAndVisible).isTrue()
    extendHalfHourAction.actionPerformed(updateAction1)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(30))
    // Extend 1 hour.
    val updateAction2 = AnActionEvent.createFromAnAction(extendOneHourAction, null, "", dataContext)
    extendOneHourAction.update(updateAction2)
    assertThat(updateAction2.presentation.isEnabledAndVisible).isTrue()
    extendOneHourAction.actionPerformed(updateAction2)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(90))
  }

  @Test
  fun testHandlesWithoutReservationActions() {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val extendReservationAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ID)!! as ExtendReservationAction
    assertThat(extendReservationAction.childrenCount).isEqualTo(3)
    val handle = FakeDeviceHandle(scope, MutableStateFlow(DeviceState.Disconnected(DeviceProperties.Builder().apply {
      icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
    }.buildBase())), null)
    val dataContext = DataContext {
      if (it == DEVICE_HANDLE_KEY.name) handle
      else null
    }
    val event = AnActionEvent.createFromAnAction(extendReservationAction, null, "", dataContext)
    extendReservationAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    val actions = extendReservationAction.getChildren(event)
    val remainingTimeEvent = AnActionEvent.createFromAnAction(actions[0], null, "", dataContext)
    actions[0].update(remainingTimeEvent)
    assertThat(remainingTimeEvent.presentation.isVisible).isFalse()
    assertThat(remainingTimeEvent.presentation.isEnabled).isFalse()

    val extendHalfHourAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
      as ExtendReservationAction.HalfHour
    val extendOneHourAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ONE_HOUR_ID)
      as ExtendReservationAction.OneHour
    // Extend 30 minutes not enabled or visible.
    val updateAction1 = AnActionEvent.createFromAnAction(extendHalfHourAction, null, "", dataContext)
    extendHalfHourAction.update(updateAction1)
    assertThat(updateAction1.presentation.isVisible).isFalse()
    assertThat(updateAction1.presentation.isEnabled).isFalse()
    // Extend 1 hour not enabled or visible.
    val updateAction2 = AnActionEvent.createFromAnAction(extendOneHourAction, null, "", dataContext)
    extendOneHourAction.update(updateAction2)
    assertThat(updateAction2.presentation.isVisible).isFalse()
    assertThat(updateAction2.presentation.isEnabled).isFalse()
  }

  @Test
  fun testHandlesWithRemainingTime() {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val extendReservationAction = CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ID)!! as ExtendReservationAction
    assertThat(extendReservationAction.childrenCount).isEqualTo(3)
    var totalDuration = Duration.ZERO

    val stateFetcher: (minutes: Long) -> DeviceState = { minutes ->
      val reservation = Reservation(ReservationState.PENDING, "None", Instant.now(), Instant.now().plusSeconds(minutes * 60 + 55))
      DeviceState.Disconnected(DeviceProperties.Builder().apply {
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
      }.buildBase(), false, "None", reservation)
    }

    val stateFlow = MutableStateFlow(stateFetcher(65))
    val handle = FakeDeviceHandle(scope, stateFlow, object : ReservationAction {
      override suspend fun reserve(duration: Duration): Instant {
        totalDuration = totalDuration.plus(duration)
        return Instant.ofEpochSecond(duration.toMillis())
      }

      override suspend fun endReservation() = Unit
      override val presentation = MutableStateFlow(defaultPresentation)
    })
    val dataContext = DataContext {
      if (it == DEVICE_HANDLE_KEY.name) handle
      else null
    }
    val event = AnActionEvent.createFromAnAction(extendReservationAction, null, "", dataContext)
    val actions = extendReservationAction.getChildren(event)
    val remainingTimeAction = AnActionEvent.createFromAnAction(actions[0], null, "", dataContext)
    actions[0].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation: 1 h 5 m remaining")

    stateFlow.value = stateFetcher(5)
    actions[0].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation: 5 m remaining")

    stateFlow.value = stateFetcher(0)
    actions[0].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation: less than 1 min remaining")
  }
}
