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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.actions.enableRichTooltip
import com.android.tools.idea.streaming.core.StreamingDeviceId
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * ToggleAction for hardware input.
 *
 * When hardware input is enabled, Android Studio forwards unaltered mouse and keyboard events to the device.
 */
internal class StreamingHardwareInputAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val displayView = getDisplayView(event) ?: return false
    return event.hardwareInputStateStorage?.isHardwareInputEnabled(displayView.deviceId) == true
  }

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val displayView = getDisplayView(event) ?: return
    event.hardwareInputStateStorage?.setHardwareInputEnabled(displayView.deviceId, selected)
    displayView.hardwareInputStateChanged(event, selected)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)

    val presentation = event.presentation
    val deviceType = getDeviceType(event)
    if (deviceType == DeviceType.AI_GLASSES || deviceType == DeviceType.XR_HEADSET && isHandAndEyeTrackingEnabled(event)) {
      presentation.isEnabledAndVisible = false
      return
    }

    val controller = getXrInputController(event)
    if (controller != null && controller.inputMode != XrInputMode.MOUSE) {
      presentation.isEnabled = false
      Toggleable.setSelected(presentation, false)
    }
    presentation.enableRichTooltip(this)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  companion object {
    const val ACTION_ID = "android.streaming.hardware.input"
  }
}

private val AnActionEvent.hardwareInputStateStorage: HardwareInputStateStorage?
  get() = project?.let { HardwareInputStateStorage.getInstance(it) }

@Service(Service.Level.PROJECT)
@State(name = "HardwareInputStateStorage", storages = [Storage("deviceHardwareInput.xml", roamingType = RoamingType.DISABLED)])
internal class HardwareInputStateStorage : PersistentStateComponent<HardwareInputStateStorage> {

  /**
   * The keys are IDs of devices for which Hardware Input is enabled. The values are times of
   * the last device access in milliseconds since epoch.
   */
  private val enabledDevices = ConcurrentHashMap<String, Long>()

  /** Visible for serialization only. Do not access directly. */
  @get:OptionTag("enabledDevices")
  @get:MapAnnotation
  var serializedEnabledDevices: Map<String, Long>
    get() = enabledDevices.toMap()
    set(value) {
      enabledDevices.clear()
      enabledDevices.putAll(value)
    }

  fun isHardwareInputEnabled(deviceId: StreamingDeviceId): Boolean {
    val key = deviceId.storageKey
    val lastUpdated = enabledDevices[key] ?: return false
    val now = System.currentTimeMillis()
    if (now - lastUpdated > REFRESH_INTERVAL.inWholeMilliseconds) {
      enabledDevices[key] = now
    }
    return true
  }

  fun setHardwareInputEnabled(deviceId: StreamingDeviceId, enabled: Boolean) {
    val key = deviceId.storageKey
    if (enabled) {
      enabledDevices[key] = System.currentTimeMillis()
    } else {
      enabledDevices.remove(key)
    }
  }

  private val StreamingDeviceId.storageKey: String
    get() =
      when (this) {
        is StreamingDeviceId.EmulatorDeviceId -> emulatorId.avdId
        is StreamingDeviceId.PhysicalDeviceId -> deviceId
      }

  private fun pruneOldDevices() {
    val now = System.currentTimeMillis()
    // Remove devices older than 30 days.
    enabledDevices.entries.removeIf { now - it.value > MAX_AGE.inWholeMilliseconds }

    // If still too many, remove the oldest.
    if (enabledDevices.size > MAX_DEVICES) {
      val sortedEntries = enabledDevices.entries.sortedBy { it.value }
      val toRemoveCount = enabledDevices.size - MAX_DEVICES
      for (i in 0 until toRemoveCount) {
        enabledDevices.remove(sortedEntries[i].key)
      }
    }
  }

  override fun getState(): HardwareInputStateStorage {
    pruneOldDevices()
    return this
  }

  override fun loadState(state: HardwareInputStateStorage) {
    XmlSerializerUtil.copyBean(state, this)
    pruneOldDevices()
  }

  companion object {
    private val MAX_AGE = 60.days
    private val REFRESH_INTERVAL = 5.seconds
    private const val MAX_DEVICES = 100

    fun getInstance(project: Project): HardwareInputStateStorage = project.service()
  }
}
