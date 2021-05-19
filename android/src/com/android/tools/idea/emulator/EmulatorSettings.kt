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
package com.android.tools.idea.emulator

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

  var launchInToolWindow = false
    get() = field
    set(value) {
      if (field != value) {
        field = value
        // Notify listeners if this is the main EmulatorSettings instance and it has been already initialized.
        if (initialized && this == getInstance()) {
          ApplicationManager.getApplication().messageBus.syncPublisher(EmulatorSettingsListener.TOPIC).emulatorSettingsChanged(this)
        }
      }
    }

  var snapshotAutoDeletionPolicy = DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY

  /**
   * Show the "AVD launched standalone" notification for a TV or automotive AVD.
   */
  var showLaunchedStandaloneNotification = true

  /**
   * Show the "AVD launched standalone" notification for a foldable AVD.
   */
  var showLaunchedStandaloneNotificationForFoldable = true

  override fun getState(): EmulatorSettings {
    return this
  }

  override fun loadState(state: EmulatorSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun initializeComponent() {
    initialized = true
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
}

val DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY = EmulatorSettings.SnapshotAutoDeletionPolicy.ASK_BEFORE_DELETING