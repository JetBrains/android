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
package com.android.tools.idea.streaming

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlin.reflect.KProperty

/**
 * Settings for mirroring of physical Android devices.
 */
@Service
@State(name = "DeviceMirroringSettingsV2", storages = [(Storage("device.mirroring.xml"))])
class DeviceMirroringSettings : PersistentStateComponent<DeviceMirroringSettings> {

  var activateOnConnection: Boolean by ChangeNotifyingProperty(false)
  var activateOnAppLaunch: Boolean by ChangeNotifyingProperty(false)
  var activateOnTestLaunch: Boolean by ChangeNotifyingProperty(false)
  var redirectAudio: Boolean by ChangeNotifyingProperty(false)
  var synchronizeClipboard: Boolean by ChangeNotifyingProperty(true)
  /** Max length of clipboard text to participate in clipboard synchronization. */
  var maxSyncedClipboardLength: Int by ChangeNotifyingProperty(MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT)
  var turnOffDisplayWhileMirroring: Boolean by ChangeNotifyingProperty(false)

  /**
   * This property indicates whether the MirroringConfirmationDialog was shown at least once.
   * It is not reflected in DeviceMirroringSettingsUi.
   */
  var confirmationDialogShown: Boolean = false

  private var initialized = false

  override fun getState(): DeviceMirroringSettings = this

  override fun noStateLoaded() {
    // Migrate previous version of settings.
    val settingsV1 = ApplicationManager.getApplication().getService(SettingsV1::class.java)
    activateOnConnection = settingsV1.activateOnConnection && settingsV1.deviceMirroringEnabled
    activateOnAppLaunch = settingsV1.activateOnAppLaunch && settingsV1.deviceMirroringEnabled
    activateOnTestLaunch = settingsV1.activateOnTestLaunch && settingsV1.deviceMirroringEnabled
    synchronizeClipboard = settingsV1.synchronizeClipboard
    maxSyncedClipboardLength = settingsV1.maxSyncedClipboardLength
    turnOffDisplayWhileMirroring = settingsV1.turnOffDisplayWhileMirroring
    confirmationDialogShown = settingsV1.confirmationDialogShown
    settingsV1.loadState(SettingsV1()) // Reset v1 settings to default.
  }

  override fun loadState(state: DeviceMirroringSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun initializeComponent() {
    initialized = true
  }

  private fun notifyListeners() {
    // Notify listeners if this is the main DeviceMirroringSettings instance, and it has been already initialized.
    if (initialized && this == getInstance()) {
      ApplicationManager.getApplication().messageBus.syncPublisher(DeviceMirroringSettingsListener.TOPIC).settingsChanged(this)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): DeviceMirroringSettings {
      return ApplicationManager.getApplication().getService(DeviceMirroringSettings::class.java)
    }

    const val MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT = 5000
  }

  private inner class ChangeNotifyingProperty<T>(var value: T) {
    operator fun getValue(thisRef: DeviceMirroringSettings, property: KProperty<*>) = value
    operator fun setValue(thisRef: DeviceMirroringSettings, property: KProperty<*>, newValue: T) {
      if (value != newValue) {
        value = newValue
        notifyListeners()
      }
    }
  }

  /** Previous version of the settings. */
  @Service
  @State(name = "DeviceMirroringSettings", storages = [(Storage("device.mirroring.xml"))])
  class SettingsV1 : PersistentStateComponent<SettingsV1> {

    var deviceMirroringEnabled: Boolean = false
    var activateOnConnection: Boolean = false
    var activateOnAppLaunch: Boolean = true
    var activateOnTestLaunch: Boolean = false
    var synchronizeClipboard: Boolean = true
    /** Max length of clipboard text to participate in clipboard synchronization. */
    var maxSyncedClipboardLength: Int = MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT
    var turnOffDisplayWhileMirroring: Boolean = false
    var confirmationDialogShown: Boolean = false

    override fun getState(): SettingsV1 = this

    override fun loadState(state: SettingsV1) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }
}