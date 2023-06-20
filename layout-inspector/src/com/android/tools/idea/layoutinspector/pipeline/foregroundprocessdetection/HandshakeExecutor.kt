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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.transport.TransportClient
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.Stream
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAutoConnectInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import layout_inspector.LayoutInspector

/**
 * Class responsible for handling all handshake logic:
 * 1. Starting the handshake for a newly connected device.
 * 3. Periodically repeating the handshake if the device has UNKNOWN support of foreground process detection.
 * 4. Logging handshake metrics.
 *
 * Handshake overview:
 *
 * When a device is connected, Layout Inspector sends a handshake requests to it, which can result in:
 * 1. SUPPORTED
 * 2. NOT_SUPPORTED
 * 3. UNKNOWN - if the device has no foreground activity (for example the device is locked)
 * or if foreground process detection is not supported on the device.
 *
 * When the support is UNKNOWN, the handshake is repeated periodically, until:
 * 1. UNKNOWN converts to SUPPORTED.
 * 2. UNKNOWN converts to NOT_SUPPORTED.
 * 3. The device is disconnected.
 *
 * This class can be re-used to execute multiple handshakes with [device].
 * For example to double-check that a NOT_SUPPORTED state is not a false negative.
 */
class HandshakeExecutor(private val device: DeviceDescriptor,
                        private val stream: Stream,
                        private val scope: CoroutineScope,
                        private val workDispatcher: CoroutineDispatcher,
                        private val transportClient: TransportClient,
                        private val metrics: ForegroundProcessDetectionMetrics,
                        private val pollingIntervalMs: Long) {
  /**
   * Channel used to communicate the handshake state with a coroutine responsible for periodically starting the handshake protocol.
   * The channel has capacity of 2 to prevent [Channel.send] to be blocking,
   * which might happen because the channel is read at intervals of [pollingIntervalMs].
   */
  private val stateChannel = Channel<HandshakeState>(2)

  private var handshakeCoordinatorJob: Job? = null

  val isHandshakeInProgress get() = when (previousState) {
    HandshakeState.Connected, is HandshakeState.UnknownSupported -> true
    HandshakeState.Disconnected, is HandshakeState.NotSupported, is HandshakeState.Supported, null -> false
  }

  /**
   * Starts a coroutine that coordinates the handshake with the device.
   */
  private fun startHandshakeCoordinator() {
    handshakeCoordinatorJob = scope.launch(workDispatcher) {
      var wasPreviousStateUnknown = false

      while (isActive) {
        // if the state has changed, use the new state. If not, use the old state.
        val state = stateChannel.tryReceive().getOrNull()
        when (state) {
          is HandshakeState.Connected, is HandshakeState.UnknownSupported -> sendStartHandshakeCommand(stream)
          null -> {
            if (wasPreviousStateUnknown) {
              // if the state was UNKNOWN and hasn't changed, keep initiating handshake.
              sendStartHandshakeCommand(stream)
            }
          }
          // stop the loop and the coroutine
          else -> break
        }

        wasPreviousStateUnknown = if (state is HandshakeState.UnknownSupported) {
          true
        }
        else {
          // the state hasn't changed.
          state == null && wasPreviousStateUnknown
        }

        delay(pollingIntervalMs)
      }
    }
  }

  /**
   * Indicates whether the previous handshake for this device terminated with a NOT_SUPPORTED state.
   * We keep track of this so that if the handshake is executed multiple times for the same device
   * and the result changes from NOT_SUPPORTED to SUPPORTED, we can log it to our metrics.
   * This can happen if the NOT_SUPPORTED was a false negative.
   */
  private var wasNotSupported = false

  private var previousState: HandshakeState? = null
    set(value) {
      // null is reserved for the initial state, it should not be set again
      checkNotNull(value)

      when (value) {
        HandshakeState.Connected, HandshakeState.Disconnected, is HandshakeState.UnknownSupported, null -> { }
        is HandshakeState.NotSupported -> wasNotSupported = true
        is HandshakeState.Supported -> wasNotSupported = false
      }

      field = value
    }

  private var isRecoveryHandshake = false

  suspend fun post(state: HandshakeState) = withContext(workDispatcher) {
    // There can be multiple projects open of Studio, all using Layout Inspector.
    // All instances of Studio will start a handshake with the same device.
    // Every instance of Studio will receive the handshake messages addressed to all other instances of Studio.
    // We don't want to act on these messages, as they would confuse our metrics and fill the [stateChannel]'s capacity.
    // The following condition is here so that we react only to state changes, as opposed to state changes + repeated states.
    // Examples of repeated states:
    //   1. repeated UNKNOWN messages because the device is locked.
    //   2. messages addressed to other instances of Studio. For example when a device is connected, all instances of Studio will initiate
    //      the handshake, the response to each handshake request is dispatched to all instances of Studio.
    if (state.javaClass == previousState?.javaClass) {
      return@withContext
    }

    when (state) {
      is HandshakeState.Connected -> {
        // the handshake coordinator is already running, don't start simultaneous handshakes in the same HandshakeExecutor.
        if (handshakeCoordinatorJob?.isActive == true) {
          return@withContext
        }

        startHandshakeCoordinator()

        // if previous state was set, it means the handshake was executed at least once before,
        // therefore this is a recovery handshake
        if (previousState != null) {
          isRecoveryHandshake = true
        }
      }
      // UNKNOWN support means that the handshake couldn't determine if the device supports foreground process detection.
      // This could be because the device is in a state where we can't determine the foreground activity,
      // for example if the device is locked and there is no foreground activity.
      // We should wait and try the handshake again until the device converts to SUPPORTED or NOT_SUPPORTED.
      is HandshakeState.UnknownSupported -> {
        if (previousState !is HandshakeState.UnknownSupported) {
          // log UNKNOWN state only once
          metrics.logHandshakeResult(state.transportEvent, device, isRecoveryHandshake)
        }
      }
      is HandshakeState.Supported -> {
        metrics.logHandshakeResult(state.transportEvent, device, isRecoveryHandshake)
        if (previousState is HandshakeState.UnknownSupported) {
          metrics.logHandshakeConversion(
            DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_SUPPORTED, device, isRecoveryHandshake
          )
        }
        if (wasNotSupported) {
          metrics.logHandshakeConversion(
            DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_NOT_SUPPORTED_TO_SUPPORTED, device, isRecoveryHandshake
          )
        }
      }
      is HandshakeState.NotSupported -> {
        metrics.logHandshakeResult(state.transportEvent, device, isRecoveryHandshake)
        if (previousState is HandshakeState.UnknownSupported) {
          metrics.logHandshakeConversion(
            DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_NOT_SUPPORTED, device, isRecoveryHandshake
          )
        }
      }
      is HandshakeState.Disconnected -> {
        if (previousState is HandshakeState.UnknownSupported) {
          // This device had UNKNOWN support and never converted to SUPPORTED or NOT_SUPPORTED.
          // This could happen if there are issues in the handshake or if a device was disconnected
          // before the UNKNOWN state had time to resolve.
          // For example if a device was plugged in while locked and unplugged before ever being unlocked.
          metrics.logHandshakeConversion(
            DynamicLayoutInspectorAutoConnectInfo.HandshakeConversion.FROM_UNKNOWN_TO_DISCONNECTED, device, isRecoveryHandshake
          )
        }
      }
    }

    stateChannel.send(state)
    previousState = state
  }

  /**
   * Sends the command that initiates the handshake, the device will respond by sending an event of type
   * LAYOUT_INSPECTOR_TRACKING_FOREGROUND_PROCESS_SUPPORTED.
   */
  private fun sendStartHandshakeCommand(stream: Stream) {
    transportClient.sendCommand(Commands.Command.CommandType.IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED, stream.streamId)
  }
}

sealed class HandshakeState {

  /**
   * It's not known if the device supports foreground process detection.
   * When the state is [UnknownSupported], we keep initiating the handshake periodically
   * until the state converges to [Supported] or [NotSupported], or the device is unplugged.
   */
  data class UnknownSupported(val transportEvent: LayoutInspector.TrackingForegroundProcessSupported): HandshakeState()
  data class Supported(val transportEvent: LayoutInspector.TrackingForegroundProcessSupported): HandshakeState()
  data class NotSupported(val transportEvent: LayoutInspector.TrackingForegroundProcessSupported): HandshakeState()
  object Connected: HandshakeState()
  object Disconnected: HandshakeState()
}