/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.LedIndicator
import com.android.emulator.control.Notification as EmulatorNotification
import com.android.emulator.control.Posture.PostureValue
import com.android.emulator.control.XrOptions
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.util.computeUserDataIfAbsent
import com.android.utils.throwIfCancellation
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Receives notifications from the emulator and updates relevant properties. */
internal class NotificationReceiver private constructor(private val emulator: EmulatorController) : Disposable, ConnectionStateListener {

  private val _currentPosture = MutableStateFlow<EmulatorConfiguration.PostureDescriptor?>(null)
  val currentPosture: StateFlow<EmulatorConfiguration.PostureDescriptor?> = _currentPosture.asStateFlow()

  private val _virtualSceneCameraActive = MutableStateFlow(false)
  val virtualSceneCameraActive: StateFlow<Boolean> = _virtualSceneCameraActive.asStateFlow()

  private val _microphoneInput = MutableStateFlow<Boolean?>(null)
  val microphoneInput: StateFlow<Boolean?> = _microphoneInput.asStateFlow()

  private val _xrOptions = MutableStateFlow<XrOptions?>(null)
  val xrOptions: StateFlow<XrOptions?> = _xrOptions.asStateFlow()

  private val _displayConfigurations = MutableStateFlow<List<DisplayConfiguration>?>(null)
  val displayConfigurations: StateFlow<List<DisplayConfiguration>?> = _displayConfigurations.asStateFlow()

  private val _ledStates = MutableStateFlow<Map<LedIndicator.Facing, Color?>>(emptyMap())
  val ledStates: StateFlow<Map<LedIndicator.Facing, Color?>> = _ledStates.asStateFlow()

  private val log = Logger.getInstance(NotificationReceiver::class.java)
  private val emulatorConfig
    get() = emulator.emulatorConfig

  private val coroutineScope = createCoroutineScope()
  private var notificationReader: Job? = null

  init {
    emulator.addConnectionStateListener(this)
  }

  private fun handleNotification(message: EmulatorNotification) {
    log.info("Received notification: ${shortDebugString(message)}")

    if (emulator.connectionState != ConnectionState.CONNECTED) {
      return
    }

    when {
      message.hasCameraNotification() -> _virtualSceneCameraActive.value = message.cameraNotification.active
      message.hasDisplayConfigurationsChangedNotification() ->
        _displayConfigurations.value = message.displayConfigurationsChangedNotification.displayConfigurations.displaysList
      message.hasPosture() -> updateCurrentPosture(message.posture.value)
      message.hasXrOptions() -> _xrOptions.value = message.xrOptions
      message.hasMicrophoneState() -> _microphoneInput.value = message.microphoneState.realAudioEnabled
      message.hasLedIndicator() -> updateLedIndicators(message.ledIndicator)
      else -> {}
    }
  }

  private fun updateCurrentPosture(posture: PostureValue) {
    val descriptor = emulatorConfig.postures.find { it.posture == posture }
    if (descriptor != null) {
      if (_currentPosture.value != descriptor) {
        _currentPosture.value = descriptor
      }
    } else {
      log.error("Unexpected posture: $posture")
    }
  }

  private fun updateLedIndicators(indicator: LedIndicator) {
    @Suppress("UseJBColor")
    val color =
      when (indicator.state) {
        LedIndicator.State.ON -> Color(indicator.color)
        else -> null
      }
    if (_ledStates.value[indicator.facing] != color) {
      _ledStates.value += (indicator.facing to color)
    }
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    stopNotificationReader()
    startNotificationReaderIfConnected()
  }

  @Synchronized
  private fun startNotificationReaderIfConnected() {
    if (emulator.connectionState == ConnectionState.CONNECTED && notificationReader == null) {
      notificationReader =
        coroutineScope.launch {
          while (isActive) {
            try {
              emulator.streamNotification().collect { message -> handleNotification(message) }
            } catch (_: EmulatorController.RetryException) {
              continue
            } catch (t: Throwable) {
              t.throwIfCancellation()
            }
            break
          }
        }
    }
  }

  @Synchronized
  private fun stopNotificationReader() {
    notificationReader?.cancel()
    notificationReader = null
  }

  override fun dispose() {
    stopNotificationReader()
  }

  companion object {
    private val key = Key<NotificationReceiver>(NotificationReceiver::class.java.simpleName)

    fun forEmulator(emulator: EmulatorController): NotificationReceiver {
      return emulator.computeUserDataIfAbsent(key) {
        val receiver = NotificationReceiver(emulator)
        Disposer.register(emulator, receiver)
        receiver
      }
    }
  }
}
