/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent Emulator-related settings.
 */
@State(name = "Emulator", storages = [Storage("emulator.xml")])
class EmulatorSettings : PersistentStateComponent<EmulatorSettings> {

  private var initialized = false

  var launchInToolWindow = true
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var synchronizeClipboard = true
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var snapshotAutoDeletionPolicy = DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  var cameraVelocityControls = DEFAULT_CAMERA_VELOCITY_CONTROLS
    set(value) {
      if (field != value) {
        field = value
        notifyListeners()
      }
    }

  override fun getState(): EmulatorSettings {
    return this
  }

  override fun loadState(state: EmulatorSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun initializeComponent() {
    initialized = true
  }

  private fun notifyListeners() {
    // Notify listeners if this is the main EmulatorSettings instance, and it has been already initialized.
    if (initialized && this == getInstance()) {
      ApplicationManager.getApplication().messageBus.syncPublisher(EmulatorSettingsListener.TOPIC).settingsChanged(this)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): EmulatorSettings {
      return ApplicationManager.getApplication().getService(EmulatorSettings::class.java)
    }
  }

  enum class SnapshotAutoDeletionPolicy(val displayName: String) {
    DELETE_AUTOMATICALLY("Delete automatically"),
    ASK_BEFORE_DELETING("Ask before deleting"),
    DO_NOT_DELETE("Do not delete")
  }

  enum class CameraVelocityControls(val keys: String, val label: String) {
    WASDQE("WASDQE", "WASDQE (for QWERTY keyboard)"),
    ZQSDAE("ZQSDAE", "ZQSDAE (for AZERTY keyboard)"),
  }
}

val DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY = EmulatorSettings.SnapshotAutoDeletionPolicy.ASK_BEFORE_DELETING
val DEFAULT_CAMERA_VELOCITY_CONTROLS = EmulatorSettings.CameraVelocityControls.WASDQE