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
import com.android.emulator.control.Notification as EmulatorNotification
import com.android.emulator.control.Posture.PostureValue
import com.android.emulator.control.XrOptions
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.util.computeUserDataIfAbsent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Receives notifications from the emulator and updates relevant properties. */
internal class NotificationReceiver private constructor(private val emulator: EmulatorController) :
  EmptyStreamObserver<EmulatorNotification>(), ConnectionStateListener {

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

  private val log = Logger.getInstance(NotificationReceiver::class.java)
  private val emulatorConfig
    get() = emulator.emulatorConfig

  @Volatile private var notificationFeed: Cancelable? = null

  init {
    emulator.addConnectionStateListener(this)
  }

  override fun onNext(message: EmulatorNotification) {
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
      else -> {}
    }
  }

  @Synchronized
  override fun onError(t: Throwable) {
    if (t is EmulatorController.RetryException) {
      cancelNotificationFeed()
      startNotificationFeedIfConnected()
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

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    cancelNotificationFeed()
    startNotificationFeedIfConnected()
  }

  private fun startNotificationFeedIfConnected() {
    if (emulator.connectionState == ConnectionState.CONNECTED) {
      notificationFeed = emulator.streamNotification(this)
    }
  }

  private fun cancelNotificationFeed() {
    notificationFeed?.cancel()
    notificationFeed = null
  }

  companion object {
    private val key = Key<NotificationReceiver>(NotificationReceiver::class.java.simpleName)

    fun forEmulator(emulator: EmulatorController): NotificationReceiver {
      return emulator.computeUserDataIfAbsent(key) { NotificationReceiver(emulator) }
    }
  }
}
