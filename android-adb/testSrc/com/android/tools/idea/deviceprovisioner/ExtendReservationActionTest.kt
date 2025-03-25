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

import com.android.mockito.kotlin.whenever
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
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

private const val EXTEND_RESERVATION_ID = "android.device.reservation.extend"
private const val EXTEND_RESERVATION_QUARTER_HOUR_ID =
  "android.device.reservation.extend.quarter.hour"
private const val EXTEND_RESERVATION_HALF_HOUR_ID = "android.device.reservation.extend.half.hour"
private val defaultPresentation = DeviceAction.Presentation("", EmptyIcon.ICON_0, true)

class ExtendReservationActionTest {

  @get:Rule val projectRule = ProjectRule()
  private val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
  private lateinit var mockInstant: MockedStatic<Instant>

  private class FakeDeviceHandle(
    override val scope: CoroutineScope,
    override val stateFlow: StateFlow<DeviceState>,
    override val reservationAction: ReservationAction?,
  ) : DeviceHandle {
    override val id = DeviceId("TEST", false, "")
  }

  @Before
  fun setup() {
    val now = Instant.now()
    mockInstant = Mockito.mockStatic(Instant::class.java, Mockito.CALLS_REAL_METHODS)
    mockInstant.whenever<Any> { Instant.now() }.thenReturn(now)
  }

  @After
  fun teardown() {
    mockInstant.close()
  }

  @Test
  fun testHandlesWithReservationActions() {
    val extendReservationAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ID)!!
        as ExtendReservationAction
    var totalDuration = Duration.ZERO
    val now = Instant.now()
    val handle =
      FakeDeviceHandle(
        scope,
        MutableStateFlow(
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
            },
            false,
            "",
            Reservation(
              ReservationState.ACTIVE,
              "",
              now,
              now.plus(30, ChronoUnit.MINUTES),
              Duration.ofHours(3),
            ),
          )
        ),
        object : ReservationAction {
          override suspend fun reserve(duration: Duration): Instant {
            totalDuration = totalDuration.plus(duration)
            return Instant.ofEpochSecond(duration.toMillis())
          }

          override suspend fun endReservation() = Unit

          override val presentation = MutableStateFlow(defaultPresentation)
        },
      )
    val dataContext = SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, handle)
    val event =
      AnActionEvent.createEvent(
        extendReservationAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    extendReservationAction.update(event)
    assertThat(event.presentation.isPerformGroup).isTrue()

    val actions = extendReservationAction.getChildren(event)
    val remainingTimeAction =
      AnActionEvent.createEvent(actions[1], dataContext, null, "", ActionUiKind.NONE, null)
    actions[1].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation: 30 min remaining")
    val extendAction =
      AnActionEvent.createEvent(actions[3], dataContext, null, "", ActionUiKind.NONE, null)

    // Verify that actions from extendReservationAction's children are equivalent with actions from
    // CustomActionsSchema.
    val extendHalfHourAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
        as ExtendReservationAction.Extend30MinOrLessAction
    val extendQuarterHourDurationAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_QUARTER_HOUR_ID)
        as ExtendReservationAction.Extend15MinOrLessAction
    assertThat(extendHalfHourAction).isEqualTo(actions[4])
    assertThat(extendQuarterHourDurationAction).isEqualTo(actions[3])

    assertThat(totalDuration).isEqualTo(Duration.ZERO)
    // Extend 30 minutes.
    val updateAction1 =
      AnActionEvent.createEvent(actions[1], dataContext, null, "", ActionUiKind.NONE, null).apply {
        presentation.isEnabledAndVisible = false
      }
    extendHalfHourAction.update(updateAction1)
    assertThat(updateAction1.presentation.isEnabledAndVisible).isTrue()
    extendHalfHourAction.actionPerformed(extendAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(30))
    // Extend Max.
    val updateAction2 =
      AnActionEvent.createEvent(
          extendQuarterHourDurationAction,
          dataContext,
          null,
          "",
          ActionUiKind.NONE,
          null,
        )
        .apply { presentation.isEnabledAndVisible = false }
    extendQuarterHourDurationAction.update(updateAction2)
    assertThat(updateAction2.presentation.isEnabledAndVisible).isTrue()
    extendQuarterHourDurationAction.actionPerformed(extendAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(45))
  }

  @Test
  fun testExtend30MinOrLessAction() {
    val maxDuration = Duration.ofMinutes(80)
    var totalDuration = Duration.ZERO
    val now = Instant.now()
    val stateFlow =
      MutableStateFlow(
        DeviceState.Disconnected(
          DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
          false,
          "",
          Reservation(
            ReservationState.ACTIVE,
            "",
            now,
            now.plus(30, ChronoUnit.MINUTES),
            maxDuration,
          ),
        )
      )
    val handle =
      FakeDeviceHandle(
        scope,
        stateFlow,
        object : ReservationAction {
          override suspend fun reserve(duration: Duration): Instant {
            val newState =
              stateFlow.value.copy(
                reservation =
                  Reservation(
                    ReservationState.ACTIVE,
                    "",
                    now,
                    now.plus(30, ChronoUnit.MINUTES).plus(duration),
                    maxDuration,
                  )
              )
            stateFlow.update { newState }
            totalDuration = totalDuration.plus(duration)
            return Instant.ofEpochSecond(duration.toMillis())
          }

          override suspend fun endReservation() = Unit

          override val presentation = MutableStateFlow(defaultPresentation)
        },
      )
    val dataContext = SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, handle)
    val extendHalfHourAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
        as ExtendReservationAction.Extend30MinOrLessAction
    assertThat(totalDuration).isEqualTo(Duration.ZERO)
    // Extend 30 minutes.
    val updateAction =
      AnActionEvent.createEvent(
        extendHalfHourAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    extendHalfHourAction.update(updateAction)
    assertThat(updateAction.presentation.isEnabledAndVisible).isTrue()
    extendHalfHourAction.actionPerformed(updateAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(30))
    extendHalfHourAction.update(updateAction)
    assertThat(updateAction.presentation.text).isEqualTo("Extend 20 mins")
    extendHalfHourAction.actionPerformed(updateAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(50))
  }

  @Test
  fun testDisablingExtend30MinOrLessAction() {
    val maxDuration = Duration.ofMinutes(70)
    var totalDuration = Duration.ZERO
    val now = Instant.now()
    val stateFlow =
      MutableStateFlow(
        DeviceState.Disconnected(
          DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
          false,
          "",
          Reservation(
            ReservationState.ACTIVE,
            "",
            now,
            now.plus(30, ChronoUnit.MINUTES),
            maxDuration,
          ),
        )
      )
    val handle =
      FakeDeviceHandle(
        scope,
        stateFlow,
        object : ReservationAction {
          override suspend fun reserve(duration: Duration): Instant {
            val newState =
              stateFlow.value.copy(
                reservation =
                  Reservation(
                    ReservationState.ACTIVE,
                    "",
                    now,
                    now.plus(30, ChronoUnit.MINUTES).plus(duration),
                    maxDuration,
                  )
              )
            stateFlow.update { newState }
            totalDuration = totalDuration.plus(duration)
            return Instant.ofEpochSecond(duration.toMillis())
          }

          override suspend fun endReservation() = Unit

          override val presentation = MutableStateFlow(defaultPresentation)
        },
      )
    val dataContext = SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, handle)
    val extendHalfHourAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
        as ExtendReservationAction.Extend30MinOrLessAction
    assertThat(totalDuration).isEqualTo(Duration.ZERO)
    // Extend 30 minutes.
    val updateAction =
      AnActionEvent.createEvent(
        extendHalfHourAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    extendHalfHourAction.update(updateAction)
    assertThat(updateAction.presentation.isEnabledAndVisible).isTrue()
    extendHalfHourAction.actionPerformed(updateAction)
    assertThat(totalDuration).isEqualTo(Duration.ofMinutes(30))
    extendHalfHourAction.update(updateAction)
    assertThat(updateAction.presentation.text).isEqualTo("Extend 30 mins")
    assertThat(updateAction.presentation.isEnabled).isFalse()
  }

  @Test
  fun testHandlesWithoutReservationActions() {
    val extendReservationAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ID)!!
        as ExtendReservationAction
    val handle =
      FakeDeviceHandle(
        scope,
        MutableStateFlow(
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
            }
          )
        ),
        null,
      )
    val dataContext = SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, handle)
    val event =
      AnActionEvent.createEvent(
        extendReservationAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    extendReservationAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    val actions = extendReservationAction.getChildren(event)
    val remainingTimeEvent =
      AnActionEvent.createEvent(actions[1], dataContext, null, "", ActionUiKind.NONE, null)
    actions[1].update(remainingTimeEvent)
    assertThat(remainingTimeEvent.presentation.isVisible).isFalse()
    assertThat(remainingTimeEvent.presentation.isEnabled).isFalse()

    val extendHalfHourAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_HALF_HOUR_ID)
        as ExtendReservationAction.Extend30MinOrLessAction
    val extendMaxDurationAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_QUARTER_HOUR_ID)
        as ExtendReservationAction.Extend15MinOrLessAction
    // Extend 30 minutes not enabled or visible.
    val updateAction1 =
      AnActionEvent.createEvent(
        extendHalfHourAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    extendHalfHourAction.update(updateAction1)
    assertThat(updateAction1.presentation.isVisible).isFalse()
    assertThat(updateAction1.presentation.isEnabled).isFalse()
    // Extend 1 hour not enabled or visible.
    val updateAction2 =
      AnActionEvent.createEvent(
        extendMaxDurationAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    extendMaxDurationAction.update(updateAction2)
    assertThat(updateAction2.presentation.isVisible).isFalse()
    assertThat(updateAction2.presentation.isEnabled).isFalse()
  }

  @Test
  fun testHandlesWithRemainingTime() {
    val extendReservationAction =
      CustomActionsSchema.getInstance().getCorrectedAction(EXTEND_RESERVATION_ID)!!
        as ExtendReservationAction
    var totalDuration = Duration.ZERO

    val stateFetcher: (minutes: Long) -> DeviceState = { minutes ->
      val reservation =
        Reservation(
          ReservationState.PENDING,
          "None",
          Instant.now(),
          Instant.now().plusSeconds(minutes * 60 + 55),
          null,
        )
      DeviceState.Disconnected(
        DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
        false,
        "None",
        reservation,
      )
    }

    val stateFlow = MutableStateFlow(stateFetcher(65))
    val handle =
      FakeDeviceHandle(
        scope,
        stateFlow,
        object : ReservationAction {
          override suspend fun reserve(duration: Duration): Instant {
            totalDuration = totalDuration.plus(duration)
            return Instant.ofEpochSecond(duration.toMillis())
          }

          override suspend fun endReservation() = Unit

          override val presentation = MutableStateFlow(defaultPresentation)
        },
      )
    val dataContext = SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, handle)
    val event =
      AnActionEvent.createEvent(
        extendReservationAction,
        dataContext,
        null,
        "",
        ActionUiKind.NONE,
        null,
      )
    val actions = extendReservationAction.getChildren(event)
    val remainingTimeAction =
      AnActionEvent.createEvent(actions[1], dataContext, null, "", ActionUiKind.NONE, null)
    actions[1].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation: 65 min remaining")

    stateFlow.value = stateFetcher(5)
    actions[1].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text).isEqualTo("Reservation: 5 min remaining")

    stateFlow.value = stateFetcher(0)
    actions[1].update(remainingTimeAction)
    assertThat(remainingTimeAction.presentation.text)
      .isEqualTo("Reservation: less than 1 min remaining")
  }
}
